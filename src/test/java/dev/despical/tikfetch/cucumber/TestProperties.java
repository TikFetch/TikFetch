package dev.despical.tikfetch.cucumber;

import dev.despical.tikfetch.config.AppProperties;

import java.nio.file.Path;

final class TestProperties {

    private TestProperties() {
    }

    static AppProperties withStorage(Path storageDirectory) {
        return new AppProperties(
            "TikFetch",
            "https://www.tikfetch.despical.dev",
            10,
            new AppProperties.Storage(storageDirectory.toString(), 10),
            new AppProperties.Cleanup(true, 3600000, 7),
            new AppProperties.Jwt("change-this-to-a-long-random-secret-at-least-256-bits", 15, 30),
            new AppProperties.Admin("admin", "admin@example.com", "change-me"),
            new AppProperties.YtDlp("yt-dlp", 120, "", "", "", "", "", "best", 60, 10, 5, 10, "exp=1:20"),
            new AppProperties.Cookies(false),
            new AppProperties.RateLimit(10, 1, 5, 5)
        );
    }
}
