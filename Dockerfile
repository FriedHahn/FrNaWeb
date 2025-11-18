# Build Stage
FROM gradle:8.8-jdk21-alpine AS build
WORKDIR /app

# Projektdateien kopieren
COPY . .

# Jar bauen (Tests überspringen, wenn sie Probleme machen)
RUN ./gradlew clean bootJar -x test --no-daemon

# Run Stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# gebautes Jar aus dem Build Container holen
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
