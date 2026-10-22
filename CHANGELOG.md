# Bag Database changelog
Unreleased
- Enforce `@Secured` method security. Modifying bags (update, upload, set/remove tags) now requires a login; saving, running and deleting scripts, `/admin/**` and `/config/**` now require `ROLE_ADMIN` (also for LDAP users). Unauthenticated AJAX requests to protected endpoints get 401 instead of a redirect to the login page.
- Stop sending secrets to the browser. `/config/get` no longer returns `jdbcPassword`, `adminPassword`, `ldapBindPassword`, `googleApiKey` or `bingKey`; saving the configuration with any of these left blank keeps the current value. `/account/current` returns only `id`, `email` and `role`.
- Fix startup failure without LDAP: since method security was enabled, the LDAP beans are created at startup and threw a NullPointerException on the empty LDAP settings written by the Docker entrypoint.
- Fail fast on a non-PostgreSQL JDBC driver instead of silently falling back to an embedded H2 database. The Docker `DB_DRIVER`, `DB_URL` and `DB_USER` defaults are now `org.postgresql.Driver`, `jdbc:postgresql://postgres/bag_database` and `bag_database`; without a `settings.yml`, the built-in defaults are `org.postgresql.Driver`, `jdbc:postgresql://localhost/bag_database` and `bag_database`.
- Dependency hygiene: remove `ldapsdk` 4.1, `javax.transaction:jta` 1.1, `tomcat-el-api`/`tomcat-embed-el` 8.5.15 and the unused `bootstrap.min.js`; drop the `byte-buddy` 1.10.10 and `objenesis` 3.2 pins and let Spring Boot manage `HikariCP` (3.4.5 → 4.0.3); `h2`, `h2gis-*` and `org.osgi.service.jdbc` are now test-scoped.
- Executable WAR: `java -jar bag-database-<version>.war` starts the application on an embedded Tomcat 9; the same WAR still deploys to an external Tomcat. `BagDatabaseApplication` replaces `WebAppInitializer`, `SecurityInitializer`, `SpringSessionInitializer` and `web.xml`; `application.properties` becomes `application.yaml` (the unused `logging.level.org.springframework.security=DEBUG` line is dropped).
  - Behavior change: the root and servlet application contexts are merged into one. Controllers, `@PostConstruct` hooks (e.g. `AdminController.checkAdminAccount`) and `@Scheduled` tasks (e.g. `ScriptService`'s result polling) now run once instead of twice.
  - The character-encoding filter now runs first, on every URL, before the session, multipart and security filters.
  - When deployed to an external Tomcat, the general error page is now served by Spring Boot's `ErrorPageFilter` (a forward) instead of a `web.xml` error page (an error dispatch), so it renders without the user's session and security context.
  - Spring Boot now initializes logging and prints its banner, and reads its usual external configuration (`application.yaml` or `config/` in the working directory, environment variables, command-line arguments). Under `java -jar`, `bag_database.log` is written to `./logs` because `catalina.base` is not set.
  - `mvn tomcat7:run` is replaced by `mvn spring-boot:run`.
- The embedded Tomcat uses the settings of the Docker image's `server.xml`, as `server.*` properties in `application.yaml`: port 8080, 150/25 threads, 20 s connection timeout, gzip compression above 2 KB, and the access log (`logs/localhost_access_log.*.txt` under the working directory, which is also Tomcat's base directory). Refused uploads are read to the end (`max-swallow-size: -1`), and shutdown is graceful for up to 8 seconds so that it completes within `docker stop`'s default 10 seconds. An external Tomcat keeps using its own `server.xml`.
- Docker image: the application runs on its embedded Tomcat (`exec java $JAVA_OPTS -jar /app/bag-database.war`) on `eclipse-temurin:11-jre` instead of `tomcat:9-jdk11`; the build stage uses `maven:3.9-eclipse-temurin-11`. `BAGDB_PATH` now sets the context path instead of renaming `ROOT.war`, `JAVA_OPTS` still passes JVM options but `CATALINA_OPTS` is no longer read, files are still created with `umask 0027`, and logs stay in `/usr/local/tomcat/logs`. `docker stop` now shuts the application down gracefully.
- Remove the S3 storage backend (contract change). `S3BagStorageImpl`, `S3BagStorageConfigImpl` and `S3BagWrapperImpl`, the AWS SDK dependencies (`software.amazon.awssdk` 2.17.4), the `docker/s3` example and its documentation page are gone; only local filesystem storage is supported. A `settings.yml` that still lists an `S3BagStorageConfigImpl` backend can no longer be parsed, so the whole file is ignored and the built-in defaults apply, including the JDBC and LDAP settings: the application will not reach its database, and saving the configuration from the web interface would overwrite `settings.yml` with those defaults. Remove that entry before upgrading.

3.5.3
- Updating Docker configuration to reference simplified paths (Thanks danthony06)

3.5.2
- Updating dependencies to address security vulnerabilities (Thanks danthony06)

3.5.1
- Improved handling of invalid metadata (Thanks pjreed and ptulpen)

3.5.0
- Update dependencies to address the Spring4Shell vulnerability (https://spring.io/blog/2022/03/31/spring-framework-rce-early-announcement) (Thanks pjreed)
- Update liquibase (Thanks pjreed)
- Fix issue where LDAP beans were being initalized even with no LDAP configuration (Thanks pjreed)
- Updated documentation (Thanks danthony06)

3.4.2
- Fix admin functions for forcing bag scanning

3.4.1
- Increase maximum bag file name length to 255 characters
- Don't prevent adding bags if the bag removal task fails

3.4.0
- Add CORS and Range support to /bags/download
- Add context menu item to open bags in external applications

3.3.0
- Refactor bag storage to allow multiple backends with different sources
- Add S3 storage backend implementation
- Increase async task timeouts to allow streaming longer videos

3.2.0

- Fix running scripts with multiple bags with the same name
- Increase maximum upload size from 4 GB to 50 GB
- Use exit code of scripts to determine success
- Support BGRA8 image encoding

3.1.0

- Add a BAGDB\_PATH environment variable to change the path used to serve the application
- Updated the reverse proxy example to use a custom path
- Fix API examples that didn't work without LDAP authentication
- Add documentation for how to use a private Docker registry
- Fix a bug that prevented private Docker registries with ports from being parsed correctly
- Improve Dockerfile to reduce rebuild times

3.0.0

- Upload & run post-processing scripts on bag files
- Upload bag files through the web interface
- UI tweaks to make various capabilities more discoverable
- Rewrite user documentation and publish via GitHub Pages
- Document REST API and include it with the web site
- Update to Java 11 & Spring 5
- Update miscellaneous other dependencies, too
- Added unit tests to cover the entire bag & script API
- Reorganized a few packages to make them more consistent
- Bug fixes:
   - Some controllers were initialized multiple times, causing unnecessary processing and weird exceptions
   - Users had to reload the page after logging in before they could view bag details
   - Many methods that modified the database did not require CSRF tokens
   - Removed AspectJ, which caused some weird build issues
   - Don't write out log file when running unit tests

2.7.3

- Add perl to Dockerfile to fix parsing GPS, metadata, and vehicle name topics
- Update dependencies

2.7.2

- Update dependencies

2.7.1

- Fix issue with indexing bags with GPS topics that don't have latitude/longitude fields
- Rewrite Dockerfile to produce smaller images
- Add docker-compose.yml for easy testing
- BagDB will wait up to 10s on startup for a database connection

2.7.0

- Fix manually setting metadata on bags without GPS coordinates
- Fix improper column types because created on newer Postgres DBs
- Fix issues decompressing LZ4-compressed chunks
- Updated dependencies

2.6.0

- Fix date rendering in grids
- Add batch inserts for GPS coordinates
- Support grayscale videos
- Lower video codec bitrate to improve performance
- Cap error list at 10000 entries

2.5.0

- Fix "Copy Link" not working in folder view
- Add ffmpeg to the Docker build
- Use ffmpeg to generate VP8 streams from Image topics
- Add optional flags to the /bags/search API call to fill in topics 
  and message types; not used by the GUI, but useful for other scripts
- Add support for editing, viewing, and searching by arbitrary tags
- Read metadata topics from bags into tags
- Remove MapQuest support
- Add support for loading tiles from generic tile map servers
- Fix a bug that would cause tabs to be blank after reloading
- Add an option to control whether entries should be removed from the DB if they are deleted from the filesystem
- Missing bags will be deleted by default
- Updated Spring & Hibernate-related dependencies to newer versions

2.4

- Fixing the mass vehicle name updater for the admin tool
- Saving widget states between reloads
- Adding more database indexes to improve search performance
- Implementing a hierarchical folder view for the bag list
- Adding some more button icons

2.3

- Updating the bag reader library to 1.5 to handle errors better
- Using Liquibase to handle database versioning and migration
- Using spatial columns for storing data for bags and their positions
- Dropping MySQL support
- Switching to H2 for the embedded database
- Removing Hibernate Search support; it wasn't actually being used and just made things slower
- Adding support for LZ4 compression
- Using Spring Session for user session tracking; sessions no longer time out

2.2

- Adding OpenCV to handle Bayer image formats
- Adding the ability to see the first image on a topic
- Adding Copy Link(s) buttons to the context menu in the grid that will copy links to download bag files to the clipboard
- Minifying Javascript for the release build
- Adding a "debugJavascript" config option that will cause the non-minified files to load
- Fixing some Javascript syntax errors

2.1

- Vehicle name topics are configurable
- GPS topics are configurable
- If no configured GPS topics are found, it will try to find one based on known message types
- Updating bag-reader-java to 1.2
- Adding Travis CI support
- Expanding on GPS-related documentation

2.0

- Adding Tomcat manually installation instructions
- Removed the "Name" database column; it was useless
- Bumping the version number to 2.0 for the open source release
- Consistently using the name "bag-database" between the Docker image and the Maven artifact name
- Adding the 3-clause BSD license
- Adding attributions for all of the dependencies
- Updated some dependencies
- Extracted bag reading a deserializing functionality into a separate library
- Changed the Maven groupId to com.github.swri-robotics
- Changed the package name to com.github.swrirobotics
- Preventing the user from dragging windows outside of the viewable area
- Fixing an issue that would make map tile credentials invisible

1.7

- Displaying visible warnings when searching by both message types and topic names
- Optimizing some CSS
- Adding the ability to download multiple bags at once
- Adding the ability to plot multiple bags on a single map
- Couldn't save bag files
- Couldn't search on the description field
- Using the Java 1.8 stream operators where possible for parallelizability

1.6

- Adding checkboxes for controlling the full text search fields
- Enabling filtering for all grid columns
- Switching the Bag grid to a BufferedStore for faster loading
- Disabling inline editing on the main grid due to an ExtJS bug
- Handling sorting and pagination on the remote side
- Adding /vehicle/gps/fix to the list of known GPS topics

1.5

- Handle relative URLs properly
- Enable gzip compression in Tomcat for Docker
- ExtJS updated to 6.0.1
- OpenLayers updated to 3.51.1
- Added an "About" window
- A navigation button has been added with links to the admin and config pages
- Administrative privileges are required to access the admin and config pages
- The admin password can be set through the config file on startup
- CSRF protection has been implemented
- Handling changing the bag path at runtime
- Added a Dockerfile and a maven plugin for building a Docker image
- Rewrote bag hashing to be more reliable
- Cleaned up and documented lots of code
- Improved bag reading performance
- Removed the "DuplicateBag" table and related functionality; it was just a workaround due to hashing bags being very slow

1.4

- Implemented generic message deserialization
- Removed hand-written deserialization classes

1.3

- Sped up bag hashing
