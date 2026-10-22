FROM maven:3.9-eclipse-temurin-11 as base-layer

LABEL maintainer="david.anthony@swri.org"
LABEL author="P.J. Reed"

# Build the bag DB in a separate stage so that the final image doesn't need
# to have the maven build environment in it.

WORKDIR /app
COPY pom.xml .
RUN mvn -e -B dependency:resolve
COPY src ./src
RUN mvn -e -B package

# The application runs on its embedded Tomcat: java -jar on the executable WAR
FROM eclipse-temurin:11-jre

LABEL maintainer="david.anthony@swri.org"
LABEL author="P.J. Reed"

# /usr/local/tomcat/logs is kept from the former tomcat:9 image so that existing log volumes keep working
VOLUME ["/bags", "/root/.ros-bag-database/indexes", "/usr/local/tomcat/logs"]
EXPOSE 8080

# Need to manually install ffmpeg and perl for streaming video
RUN apt-get update \
    && apt-get install -y ffmpeg perl \
    && rm -rf /var/lib/apt/lists/*

# The working directory is Tomcat's base directory (server.tomcat.basedir=.), so bag_database.log and the
# access log are written to /usr/local/tomcat/logs
WORKDIR /usr/local/tomcat
COPY --from=base-layer /app/target/*.war /app/bag-database.war
COPY src/main/docker/entrypoint.sh /

CMD ["/entrypoint.sh"]
