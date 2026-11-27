# ── Build ───────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

# ── Runtime ─────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system finny && useradd --system --gid finny --no-create-home finny
COPY --from=build /app/build/libs/*.jar app.jar
USER finny
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=America/Sao_Paulo"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
