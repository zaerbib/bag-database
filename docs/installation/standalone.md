---
layout: default
title: Without Docker
parent: Installation
nav_order: 2
description: "Running the Bag Database with java -jar, without Docker"
permalink: /installation/standalone
---

# Running without Docker

The Bag Database is a Spring Boot application with an embedded Tomcat: it is started with
`java -jar` and does not need an application server.  Deploying the WAR file into an external
Tomcat is no longer supported.

## Requirements

- Java 11 (a JRE is enough to run it; building needs a JDK 11 and Maven 3.0.5 or later).
- A PostgreSQL server with the PostGIS extension.
- `ffmpeg` version 3 or higher on the system path, for video streaming.

## Building

`mvn package`

This produces `target/bag-database-<version>.war`, an executable archive that contains the embedded
Tomcat.

## Running

1. Start up a PostgreSQL server with PostGIS; create an empty database and a user with access to it.
2. Create `${HOME}/.ros-bag-database/settings.yml`, where `${HOME}` is the home directory of the user
that runs the application, and set your configuration.  Here's an example of a valid config file; keys
you don't want to set can be omitted.

    ```yml
    !com.github.swrirobotics.support.web.Configuration
    bingKey: PKnOQDvUxRJ0bEZdBH7m
    dockerHost: http://localhost:2375
    driver: org.postgresql.Driver
    googleApiKey: PKnOQDvUxRJ0bEZdBH7m
    gpsTopics: 
    - /localization/gps
    - /gps
    - /vehicle/gps/fix
    jdbcPassword: letmein
    jdbcUrl: jdbc:postgresql://bagdb-postgres/bag_database
    jdbcUsername: bag_database
    useBing: true
    useMapQuest: false
    vehicleNameTopics: 
    - /vms/vehicle_name
    ```

3. Start the application:

    ```bash
    java -jar bag-database-<version>.war
    ```

    It listens on port 8080.  Logs are written to `logs/` under the working directory:
    `bag_database.log` and the HTTP access log.
4. Look inside `logs/bag_database.log` to find the automatically-generated administrator password.
5. Log in through the GUI and use the Maintenance panel to change the password.

## Server settings

Settings that used to live in Tomcat's `server.xml` or `setenv.sh` are now Spring Boot properties or
JVM options, passed on the command line:

- Server settings are `server.*` properties, for example `--server.port=9090` or
`--server.servlet.context-path=/bagdb` to serve the application from a subdirectory.  The defaults are
in `application.yaml` inside the archive; an `application.yaml` in the working directory, or in its
`config/` subdirectory, overrides them.
- JVM options go before `-jar`, for example `java -Xmx4g -jar bag-database-<version>.war`.

## Running as a service

Here's an example `systemd` unit, `/etc/systemd/system/bag-database.service`:

```ini
[Unit]
Description=Bag Database
After=network.target postgresql.service

[Service]
User=bagdb
WorkingDirectory=/opt/bag-database
ExecStart=/usr/bin/java -Xmx4g -jar /opt/bag-database/bag-database.war
SuccessExitStatus=143
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

The application reads `settings.yml` from the home directory of the `bagdb` user and writes its logs to
`/opt/bag-database/logs`.  `systemctl stop` sends `SIGTERM`, and the application shuts down
gracefully.
