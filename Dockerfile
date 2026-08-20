FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
ARG CREDICASH_BACKEND_VERSION="1.1.0"
RUN echo "Building Credicash Backend ${CREDICASH_BACKEND_VERSION} for Railway"
COPY . ./
RUN test -f gradle/wrapper/gradle-wrapper.jar || (echo "ERROR: falta gradle/wrapper/gradle-wrapper.jar en el contexto Docker" >&2; exit 1)
RUN chmod +x gradlew && ./gradlew --no-daemon --console=plain --max-workers=2 clean test buildFatJar --stacktrace
RUN test -f build/libs/credicash-server-all.jar || (echo "ERROR: no se generó el Fat JAR del backend" >&2; exit 1)

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/credicash-server-all.jar /app/credicash-server-all.jar
ENV PORT=8080 \
    UPLOAD_DIR=/data/uploads \
    JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"
RUN mkdir -p /data/uploads
EXPOSE 8080
CMD ["java", "--enable-native-access=ALL-UNNAMED", "-XX:InitialRAMPercentage=20.0", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/credicash-server-all.jar"]
