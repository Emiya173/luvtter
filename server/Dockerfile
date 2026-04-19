FROM gradle:9.1-jdk17 AS build
WORKDIR /app
COPY --chown=gradle:gradle . .
RUN gradle :server:buildFatJar --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/server/build/libs/*-all.jar /app/server.jar
EXPOSE 8080
CMD ["java", "-jar", "/app/server.jar"]
