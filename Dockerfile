# Multi-stage -- a host that builds straight from this Dockerfile against
# the git repo (e.g. Render) has no separate "run mvn package first" step the way local
# docker-compose usage does via start.sh; a single-stage Dockerfile assuming a pre-built
# target/*.jar fails there with "lstat /target: no such file or directory", confirmed live against
# a real Render deploy. This stage builds the jar itself instead of assuming one already exists.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# pom.xml copied and dependencies resolved before the source, so this layer -- by far the slowest
# part of the build -- is only re-run when dependencies actually change, not on every source edit.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/app
COPY --from=build /build/target/*.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
