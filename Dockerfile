# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:25-jdk-noble AS build

WORKDIR /workspace

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl gnupg \
    && curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

COPY gradlew gradlew.bat settings.gradle build.gradle package.json package-lock.json vite.config.js ./
COPY gradle ./gradle
COPY frontend ./frontend
COPY src ./src

RUN chmod +x ./gradlew \
    && ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:25-jre-noble

ENV SPRING_PROFILES_ACTIVE=production \
    SERVER_PORT=8080 \
    APP_STORAGE_DIR=/var/lib/tikfetch/storage \
    YT_DLP_PATH=/usr/local/bin/yt-dlp \
    YT_DLP_FFMPEG_LOCATION=/usr/bin \
    JAVA_OPTS=""

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl ffmpeg python3 python3-venv \
    && python3 -m venv /opt/yt-dlp \
    && /opt/yt-dlp/bin/pip install --no-cache-dir --upgrade --pre "yt-dlp[default,curl-cffi]" \
    && ln -s /opt/yt-dlp/bin/yt-dlp /usr/local/bin/yt-dlp \
    && /usr/local/bin/yt-dlp --version \
    && /usr/local/bin/yt-dlp --list-impersonate-targets | grep -q Chrome \
    && useradd --system --create-home --home-dir /app --shell /usr/sbin/nologin tikfetch \
    && mkdir -p /var/lib/tikfetch/storage \
    && chown -R tikfetch:tikfetch /app /var/lib/tikfetch \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /workspace/build/libs/*.jar /app/tikfetch.jar

USER tikfetch

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl -fsS http://127.0.0.1:${SERVER_PORT}/actuator/health >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/tikfetch.jar"]
