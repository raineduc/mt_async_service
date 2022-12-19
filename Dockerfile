# syntax=docker/dockerfile:1

FROM openjdk:11
COPY target/ifuture_task-1.0.0-SNAPSHOT-fat.jar app.jar
ENTRYPOINT java -jar app.jar