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

package dev.despical.tikfetch.controller;

import dev.despical.tikfetch.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Despical
 * <p>
 * Created at 15.08.2026
 */
@RestController
@RequiredArgsConstructor
public class SeoController {

    private final AppProperties properties;

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public String robots() {
        return """
            User-agent: *
            Allow: /
            Disallow: /actuator/
            Disallow: /media/

            Sitemap: %s/sitemap.xml
            """.formatted(baseUrl());
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                <url>
                    <loc>%s/</loc>
                    <changefreq>weekly</changefreq>
                    <priority>1.0</priority>
                </url>
            </urlset>
            """.formatted(baseUrl());
    }

    private String baseUrl() {
        return properties.baseUrl().replaceAll("/+$", "");
    }
}
