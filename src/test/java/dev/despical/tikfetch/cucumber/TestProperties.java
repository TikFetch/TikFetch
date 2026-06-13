/*
 * TikFetch - A clean web app for saving TikTok videos and photo posts.
 * Copyright (C) 2026  Berke Akçen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
