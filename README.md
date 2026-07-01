# TikFetch

[![](https://github.com/TikFetch/TikFetch/actions/workflows/build.yaml/badge.svg)](https://github.com/TikFetch/TikFetch/actions/workflows/build.yaml)
[![](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Java 25](https://img.shields.io/badge/Java-25-007396.svg)](https://www.java.com/)
![Gradle](https://img.shields.io/badge/Gradle-9.6.1-079ec0?logo=gradle&logoColor=white)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F.svg)](https://spring.io/projects/spring-boot)

TikFetch is an ultra-clean, lightning-fast web application designed to save public TikTok videos, photo posts, swipeable photo galleries, and MP3 audio without watermark clutter, annoying popups, or ad-heavy distractions.

Most downloaders overwhelm you with intrusive redirects, intrusive redirects, and multiple steps just to get a single file. TikFetch changes that by providing a premium, focused, and completely private experience. Paste your link, preview your media instantly, and download high-quality files in seconds.

---

## Features

* **Watermark-free downloads:** Save public TikTok videos in MP4 format and photo galleries as high-quality image files.
* **MP3 audio:** Extract MP3 audio from supported video posts when `ffmpeg` and `ffprobe` are available.
* **Zero distractions:** No hidden ad-links, no noisy redirects, and no endless popups. Just your media, instantly.
* **Private preview pages:** Check your fetched media on a dedicated, minimal results page before saving.
* **Self-hostable freedom:** Fully configurable framework allowing you to run your own dedicated, private instance.

---

## Requirements

* Java 25
* Node.js 22 or newer
* MySQL 8 or compatible
* `yt-dlp`
* `ffmpeg` and `ffprobe` for MP3 extraction

---

## Building From Source

### 1. Clone the Repository

```bash
git clone https://github.com/TikFetch/TikFetch.git
cd TikFetch
```

### 2. Environment Configuration

Create your local environment file to configure your database, storage paths, and tools.

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

Linux / macOS:

```bash
cp .env.example .env
```

Open `.env` and fill in your database credentials, storage directory, JWT secret, admin credentials, and binary paths.

### 3. Build & Test

Windows:

```bat
gradlew.bat test
gradlew.bat build
```

Linux / macOS:

```bash
./gradlew test
./gradlew build
```

### 4. Run Locally

Windows:

```bat
gradlew.bat bootRun
```

Linux / macOS:

```bash
./gradlew bootRun
```

Once started, the application will be accessible at: `http://localhost:8080/`

---

## yt-dlp

TikFetch uses `yt-dlp` for media fetching. Ensure the binary path is correctly set in your `.env` file.

Windows example:

```env
YT_DLP_PATH=C:/Program Files/yt-dlp/yt-dlp.exe
YT_DLP_FFMPEG_LOCATION=C:/Program Files/ffmpeg/bin
YT_DLP_COOKIES_PATH=C:/tikfetch/cookies/tiktok-cookies.txt
```

Linux example:

```env
YT_DLP_PATH=/usr/local/bin/yt-dlp
YT_DLP_FFMPEG_LOCATION=/usr/bin
YT_DLP_COOKIES_PATH=/opt/tikfetch/cookies/tiktok-cookies.txt
```

If your server region encounters restriction layers, you can route requests through a proxy directly via config:

```env
YT_DLP_PROXY=http://user:password@host:port
```

---

## Security

We prioritize user privacy and application integrity. Please do not open public issues for discovered vulnerabilities.

Read our [SECURITY.md](SECURITY.md) for responsible disclosure reporting.

---

## Contributing

We welcome Pull Requests from the community. To help us maintain clean project history and formatting, please follow these guidelines:

* **No tabs:** Use spaces exclusively for indentation.
* **Style consistency:** Respect the established code architecture and style templates.
* **Version control cleanliness:** Do not increment project version numbers in example configurations within your PR.
* **Minimal diffs:** Disable automated reformat-on-save settings that affect untouched files.

Learn more via our formal [Contribution Guidelines](CONTRIBUTING.md).

---

## License

This project is licensed under the [GPL-3.0 License](http://www.gnu.org/licenses/gpl-3.0.html).

See the [LICENSE](LICENSE) file for comprehensive copyright notices and third-party attributions.
