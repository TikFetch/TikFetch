# TikFetch

[![CI](https://github.com/Despical/TikFetch/actions/workflows/ci.yml/badge.svg)](https://github.com/Despical/TikFetch/actions/workflows/ci.yml)
[![License: PolyForm Noncommercial](https://img.shields.io/badge/license-PolyForm%20Noncommercial-blue.svg)](LICENSE)
[![Java 25](https://img.shields.io/badge/Java-25-007396.svg)](https://www.java.com/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F.svg)](https://spring.io/projects/spring-boot)

TikFetch is a clean web app for saving public TikTok videos and photo posts without watermark clutter, popups, or ad-heavy distractions.

Paste a public TikTok URL, fetch the media, preview it on a private result page, and download MP4 videos or JPEG photo galleries. TikFetch also includes an admin panel for recent downloads, attempts, storage health, and runtime metrics.

TikFetch is an independent project and is not affiliated with TikTok, ByteDance Ltd., or any related platform.

## Features

- Download public TikTok videos as MP4 files.
- Download public TikTok photo posts and swipeable photo galleries as JPEG files.
- Preview each fetch on a unique result page.
- Keep latest downloads easy to find without mixing users into one shared result.
- View download attempts, failures, storage usage, health, and metrics from an admin panel.
- Store media on disk while keeping metadata in MySQL.
- Clean up old successful downloads automatically.
- Protect public downloads and admin login with rate limits.

## Requirements

- Java 25
- Node.js 22 or newer
- MySQL 8 or compatible
- `yt-dlp`

## Building From Source

Clone the repository:

```bash
git clone https://github.com/Despical/TikFetch.git
cd TikFetch
```

Create a local environment file:

```bash
cp .env.example .env
```

Edit `.env` with your database credentials, admin credentials, storage directory, JWT secret, and `yt-dlp` path.

Build and test:

```bash
./gradlew test
./gradlew build
```

On Windows:

```bat
gradlew.bat test
gradlew.bat build
```

Run locally:

```bash
./gradlew bootRun
```

The public site runs at:

```text
http://localhost:8080/
```

The admin panel runs at:

```text
http://localhost:8080/admin
```

## Profiles

TikFetch uses Spring profiles:

- `development`: default local profile with forgiving local defaults.
- `production`: real deployment profile that expects secrets and infrastructure values from environment variables.

Run production profile:

```bash
SPRING_PROFILES_ACTIVE=production ./gradlew bootRun
```

On Windows PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = "production"
.\gradlew.bat bootRun
```

## Configuration

Important environment values:

```env
SPRING_PROFILES_ACTIVE=development

APP_NAME=TikFetch
APP_BASE_URL=http://localhost:8080
APP_STORAGE_DIR=C:/tikfetch/storage

MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DATABASE=tikfetch
MYSQL_USERNAME=root
MYSQL_PASSWORD=password

JWT_SECRET=change-this-to-a-long-random-secret-at-least-256-bits

ADMIN_USERNAME=admin
ADMIN_EMAIL=admin@example.com
ADMIN_PASSWORD=change-me

YT_DLP_PATH=yt-dlp
COOKIE_SECURE=false
```

For production:

- Use `SPRING_PROFILES_ACTIVE=production`.
- Set `COOKIE_SECURE=true`.
- Use a long random `JWT_SECRET`.
- Use a dedicated MySQL user.
- Store media outside the repository.
- Put the app behind HTTPS.

## yt-dlp

TikFetch shells out to `yt-dlp`. On Windows, a path like this is valid:

```env
YT_DLP_PATH=C:/Program Files/yt-dlp/yt-dlp.exe
```

If TikTok blocks requests from your server region, configure cookies or a proxy:

```env
YT_DLP_COOKIES_PATH=C:/tikfetch/cookies/tiktok-cookies.txt
YT_DLP_PROXY=http://user:password@host:port
```

## GitHub Actions

The CI workflow runs Gradle tests and the frontend build on every push and pull request.

## Contributing

Contributions are welcome for bug fixes, UI polish, documentation, and safer download handling. Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.

## Security

Please do not open public issues for vulnerabilities. Read [SECURITY.md](SECURITY.md) for responsible reporting.

## License

TikFetch is licensed under the [PolyForm Noncommercial License 1.0.0](LICENSE).

Commercial use is not permitted without separate permission from the copyright holder.
