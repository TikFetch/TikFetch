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

package dev.despical.tikfetch.service.download;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * @author Despical
 * <p>
 * Created at 30.08.2026
 */
@Component
public class RemoteMediaSessionStore {

    private static final Duration SESSION_LIFETIME = Duration.ofMinutes(15);

    private final ConcurrentHashMap<Long, Session> sessions = new ConcurrentHashMap<>();

    public void put(Long videoId, String cookieHeader) {
        sessions.put(videoId, new Session(cookieHeader, Instant.now().plus(SESSION_LIFETIME)));
    }

    public Optional<String> cookieHeader(Long videoId) {
        Session session = sessions.get(videoId);

        if (session == null) {
            return Optional.empty();
        }

        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(videoId, session);
            return Optional.empty();
        }

        return Optional.ofNullable(session.cookieHeader());
    }

    public void remove(Long videoId) {
        sessions.remove(videoId);
    }

    private record Session(String cookieHeader, Instant expiresAt) {
    }
}
