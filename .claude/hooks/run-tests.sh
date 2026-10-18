#!/usr/bin/env bash
# Stop hook: when src/ or pom.xml changed since the last green run, run `mvn -B test`
# and hand failures back to Claude (decision=block) so it fixes them before finishing.
#
# Env knobs:
#   BAGDB_SKIP_TESTS=1        disable the hook
#   BAGDB_FORCE_TESTS=1       run even if nothing changed / already green
#   BAGDB_JAVA_HOME=<path>    JDK to run Maven with (the project targets Java 11)
#   BAGDB_TEST_GOAL="..."     override the Maven arguments (default: -B test)

input=$(cat)

[ "${BAGDB_SKIP_TESTS:-0}" = "1" ] && exit 0
# Already blocked once in this stop cycle: let Claude stop to avoid a loop.
[ "$(printf '%s' "$input" | jq -r '.stop_hook_active // false')" = "true" ] && exit 0

cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || exit 0

force="${BAGDB_FORCE_TESTS:-0}"
# Nothing relevant changed -> nothing to test.
[ "$force" != "1" ] && [ -z "$(git status --porcelain -- src pom.xml)" ] && exit 0

state_dir="${TMPDIR:-/tmp}/bagdb-claude-hooks"
mkdir -p "$state_dir"
fingerprint=$( {
  git diff HEAD -- src pom.xml
  git ls-files -o --exclude-standard -- src pom.xml | while IFS= read -r f; do
    printf '%s\n' "$f"; cat "$f"
  done
} | sha1sum | cut -d' ' -f1 )
[ "$force" != "1" ] && [ "$fingerprint" = "$(cat "$state_dir/last-green" 2>/dev/null)" ] && exit 0

# One Maven run at a time.
lock="$state_dir/lock"
if ! mkdir "$lock" 2>/dev/null; then
  jq -n '{systemMessage: "run-tests hook: another Maven run is in progress; skipped."}'
  exit 0
fi
trap 'rmdir "$lock"' EXIT

if [ -n "${BAGDB_JAVA_HOME:-}" ]; then
  export JAVA_HOME="$BAGDB_JAVA_HOME"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
java_major=$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)

log="$state_dir/mvn-test.log"
# shellcheck disable=SC2086
mvn ${BAGDB_TEST_GOAL:--B test} >"$log" 2>&1
status=$?

if [ $status -eq 0 ]; then
  printf '%s' "$fingerprint" >"$state_dir/last-green"
  summary=$(grep -E 'Tests run:.*Fail' "$log" | tail -1)
  jq -n --arg s "run-tests hook: mvn test passed (JDK $java_major). $summary" '{systemMessage: $s}'
  exit 0
fi

details=$( {
  grep -E '^\[ERROR\] (Tests run|Failures|Errors|  [A-Za-z].*(Test|IT)\.)|\[ERROR\] .*\.java:\[[0-9]+' "$log" | head -40
  echo '--- tail ---'
  tail -25 "$log"
} )
note=""
[ "$java_major" != "11" ] && note=" Note: Maven ran on JDK $java_major but the project targets Java 11; set BAGDB_JAVA_HOME if failures look environment-related."

jq -n --arg r "mvn test FAILED (exit $status). Fix the failures before finishing, or explain why they are pre-existing.$note Full log: $log
$details" '{decision: "block", reason: $r}'
exit 0
