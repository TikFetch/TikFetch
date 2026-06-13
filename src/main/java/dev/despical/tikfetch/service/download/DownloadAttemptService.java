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

import dev.despical.tikfetch.entity.DownloadAttempt;
import dev.despical.tikfetch.entity.DownloadStatus;
import dev.despical.tikfetch.repository.DownloadAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@Service
@RequiredArgsConstructor
public class DownloadAttemptService {

    private final DownloadAttemptRepository attemptRepository;

    @Transactional
    public void record(String submittedUrl, String normalizedUrl, DownloadStatus status, String message, String clientIp) {
        DownloadAttempt attempt = new DownloadAttempt();
        attempt.setSubmittedUrl(submittedUrl == null ? "" : submittedUrl);
        attempt.setNormalizedUrl(normalizedUrl);
        attempt.setStatus(status);
        attempt.setMessage(message);
        attempt.setClientIp(clientIp);

        attemptRepository.save(attempt);
    }
}
