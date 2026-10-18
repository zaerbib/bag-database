#!/usr/bin/env bash
# PreToolUse guard for Edit/Write/NotebookEdit/Bash.
# Enforces the refactoring-plan ground rules (.claude/docs/refactoring-plan.md)
# and the safety sections of the skills in .claude/skills/.
# Output: a permissionDecision of "deny" or "ask" as JSON; silence = no opinion.

input=$(cat)
tool=$(printf '%s' "$input" | jq -r '.tool_name // empty')

decide() { # $1 = deny|ask, $2 = reason
  jq -n --arg d "$1" --arg r "$2" \
    '{hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: $d, permissionDecisionReason: $r}}'
  exit 0
}

# C:\foo\bar or C:/foo/bar -> /c/foo/bar
norm() { printf '%s' "$1" | sed -e 's#\\#/#g' -e 's#^\([A-Za-z]\):#/\L\1#'; }

root=$(norm "${CLAUDE_PROJECT_DIR:-$(pwd)}")

case "$tool" in
  Edit|Write|NotebookEdit)
    path=$(printf '%s' "$input" | jq -r '.tool_input.file_path // .tool_input.notebook_path // empty')
    [ -z "$path" ] && exit 0
    abs=$(norm "$path")
    rel=${abs#"$root"/}

    case "$rel" in
      # --- hard denies ---------------------------------------------------
      src/main/resources/db/changelog/db.changelog-master.yaml)
        ;; # appending includes is allowed
      src/main/resources/db/changelog/db.changelog-*.yaml)
        if [ -e "$abs" ]; then
          decide deny "Existing Liquibase changelogs must never be edited (plan ground rules). Create a new db.changelog-X.Y.yaml with a rollback block instead - see the liquibase-changeset skill."
        fi ;;
      target/*|*/target/*)
        decide deny "target/ is build output (WAR, surefire reports, generated metamodel). Change the sources instead." ;;
      .git/*)
        decide deny "Direct edits inside .git/ are not allowed." ;;
      *settings.yml|*.pem|*.key|*.p12|*.jks|.env|.env.*|*/.env)
        decide deny "Secret/credential file. settings.yml holds jdbcPassword, adminPassword, ldapBindPassword and API keys (audit 6.3)." ;;

      # --- confirmation required -----------------------------------------
      .claude/settings.json|.claude/settings.local.json|.claude/hooks/*)
        decide ask "This changes Claude Code hooks/settings that protect the repo. Confirm." ;;
      .github/workflows/*)
        decide ask "CI workflow change (only expected in plan steps 2, 18, 25). Confirm." ;;
      src/main/webapp/resources/js/external/*)
        decide ask "Vendored third-party JS (ExtJS, OpenLayers, sockjs, stomp). Only replace in plan step 26, one library per PR. Confirm." ;;
      docs/*)
        decide ask "docs/ is the public Jekyll/GitHub Pages site. Audit and plan material belongs in .claude/docs/. Confirm." ;;
      .claude/docs/audit.md)
        decide ask "The audit is a read-only record; only the /audit command should rewrite it. Confirm." ;;
      CLAUDE.md|LICENSE*)
        decide ask "Project-wide instructions/licence file. Confirm." ;;
    esac
    ;;

  Bash)
    cmd=$(printf '%s' "$input" | jq -r '.tool_input.command // empty')
    [ -z "$cmd" ] && exit 0

    # --- hard denies -------------------------------------------------------
    if printf '%s' "$cmd" | grep -Eq '(^|[;&|[:space:]])git[[:space:]]+push\b'; then
      decide deny "git push is not allowed from Claude (next-step: 'Do not git push'). Push yourself after review."
    fi
    if printf '%s' "$cmd" | grep -Eq -- '--no-verify|--no-gpg-sign'; then
      decide deny "Skipping git hooks or signing is not allowed."
    fi
    if printf '%s' "$cmd" | grep -Eq '\bgit[[:space:]]+(reset[[:space:]]+--hard|clean[[:space:]]+-[a-zA-Z]*f|checkout[[:space:]]+--[[:space:]]+\.|restore[[:space:]]+\.)'; then
      decide deny "Destructive git command would discard uncommitted work. Do it manually if intended."
    fi
    if printf '%s' "$cmd" | grep -Eq '\brm[[:space:]]+-[a-zA-Z]*r[a-zA-Z]*[[:space:]]+.*(\bsrc\b|\.git\b|\.claude\b|pom\.xml)'; then
      decide deny "Recursive delete of source, .git or .claude is not allowed."
    fi
    if printf '%s' "$cmd" | grep -Eq '(sed[[:space:]]+-i|perl[[:space:]]+-[a-z]*i|\b(rm|mv|tee|truncate))[^|;&]*db\.changelog-[0-9]|>[[:space:]]*[^[:space:]]*db\.changelog-[0-9]'; then
      decide deny "Existing Liquibase changelogs must never be modified (plan ground rules). Use the liquibase-changeset skill."
    fi

    # --- confirmation required ----------------------------------------------
    if printf '%s' "$cmd" | grep -Eq '\bgit[[:space:]]+tag\b'; then
      decide ask "Creating git tags (pre-<step>) needs your confirmation (version-upgrade skill)."
    fi
    if printf '%s' "$cmd" | grep -Eq '\bdocker([[:space:]]+(run|build|pull|push|compose)|-compose)\b'; then
      decide ask "Docker builds/pulls/compose runs need your confirmation (skills' safety rules)."
    fi
    ;;
esac

exit 0
