# TikFetch

[![](https://github.com/TikFetch/TikFetch/actions/workflows/build.yaml/badge.svg)](https://github.com/TikFetch/TikFetch/actions/workflows/build.yaml)
[![](https://img.shields.io/badge/License-GPLv3-blue.svg)](../LICENSE)
[![Java 25](https://img.shields.io/badge/Java-25-007396.svg)](https://www.java.com/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F.svg)](https://spring.io/projects/spring-boot)

TikFetch is an ultra-clean, lightning-fast web application designed to save public TikTok videos and photo posts without any watermark clutter, annoying popups, or ad-heavy distractions.

Most downloaders overwhelm you with tracking scripts, intrusive redirects, and multiple steps just to get a single file. TikFetch changes that by providing a premium, focused, and completely private experience. Paste your link, preview your media instantly, and download high-quality files in seconds.

---

## ✨ Features

* **Watermark-Free Downloads:** Save public TikTok videos in crisp MP4 format and photo galleries as high-quality JPEG items.
* **Zero Distractions:** No hidden ad-links, no noisy redirects, and no endless popups. Just your media, instantly.
* **Private Preview Pages:** Check your fetched media on a dedicated, minimal results page before saving.
* **Blazing Fast Performance:** Powered by a modern reactive backend to bridge the gap between the link and your local file seamlessly.
* **Self-Hostable Freedom:** Fully configurable framework allowing you to run your own dedicated, private instance.

---

## 💻 Requirements

* Java 25
* Node.js 22 or newer
* MySQL 8 or compatible
* `yt-dlp`

---

## 🛠️ Building From Source

### 1. Clone the Repository
```bash
git clone [https://github.com/TikFetch/TikFetch.git](https://github.com/TikFetch/TikFetch.git)
cd TikFetch
```

### 2. Environment Configuration
Create your local environment file to configure your database, storage paths, and tools.

* **Windows PowerShell:**
    ```powershell
    Copy-Item .env.example .env
    ```
  
* **Linux / macOS:**
    ```bash
    cp .env.example .env
    ```

> 📝 **Note:** Open the newly created `.env` file and fill in your database credentials, storage directory, JWT secret, and the path to your `yt-dlp` binary.

### 3. Build & Test

* **Windows:**
    ```bat
    gradlew.bat test
    gradlew.bat build
    ```
  
* **Linux / macOS:**
    ```bash
    ./gradlew test
    ./gradlew build
    ```

### 4. Run Locally

* **Windows:**
    ```bat
    gradlew.bat bootRun
    ```
  
* **Linux / macOS:**
    ```bash
    ./gradlew bootRun
    ```

Once started, the application will be accessible at: `http://localhost:8080/`

---

## yt-dlp

TikFetch leverages `yt-dlp` natively for robust and efficient media fetching. Ensure the binary path is correctly set in your `.env` file.

* **Windows Configuration Example:**
    ```env
    YT_DLP_PATH=C:/Program Files/yt-dlp/yt-dlp.exe
    YT_DLP_COOKIES_PATH=C:/tikfetch/cookies/tiktok-cookies.txt
    ```
  
* **Linux Configuration Example:**
    ```env
    YT_DLP_PATH=/usr/local/bin/yt-dlp
    YT_DLP_COOKIES_PATH=/opt/tikfetch/cookies/tiktok-cookies.txt
    ```

If your server region encounters restriction layers, you can easily route requests through a proxy directly via config:
```env
YT_DLP_PROXY=http://user:password@host:port
```

---

## 🔒 Security

We prioritize user privacy and application integrity. Please do not open public issues for discovered vulnerabilities.

Read our [SECURITY.md](../SECURITY.md) for responsible disclosure reporting.

---

## 🤝 Contributing

We welcome Pull Requests from the community! To help us maintain clean project history and formatting, please follow these guidelines:
* **No Tabs:** Use spaces exclusively for indentation.
* **Style Consistency:** Respect the established code architecture and style templates.
* **Version Control Cleanliness:** Do not increment project version numbers in example configurations within your PR.
* **Minimal Diffs:** Disable automated "reformat on save" settings that affect untouched files. If a refactor is needed, please submit it as an independent PR.

Learn more via our formal [Contribution Guidelines](../CONTRIBUTING.md).

---

## 📄 License

This project is licensed under the [GPL-3.0 License](http://www.gnu.org/licenses/gpl-3.0.html).  

See the [LICENSE](../LICENSE) file for comprehensive copyright notices and third-party attributions.
