/*
 * TikFetch - A clean web app for saving TikTok videos and photo posts.
 * Copyright (C) 2026  Berke Akçen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.despical.tikfetch.service;

/**
 * Signals that the shared latest-video snapshot must be rebuilt after the
 * surrounding database transaction commits.
 *
 * @author Despical
 */
public record LatestVideosChangedEvent() {
}
