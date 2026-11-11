# Two stages: build the jar with Maven, then run it on a small Java 17 runtime image.
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/target/quiz-system-0.1.0-SNAPSHOT.jar /app/quiz-system.jar
# The database and the instructor key live here; mount a volume to keep them between runs.
VOLUME /data
EXPOSE 8080
# The instructor link (with its key) is printed in the log at start-up: "Instructor dashboard: http://localhost:8080/...".
ENTRYPOINT ["java", "-jar", "/app/quiz-system.jar", \
            "--spring.datasource.url=jdbc:h2:file:/data/quizdb", \
            "--quiz.instructor-key-file=/data/instructor.key"]
