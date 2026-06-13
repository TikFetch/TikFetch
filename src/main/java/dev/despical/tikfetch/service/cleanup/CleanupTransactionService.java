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

package dev.despical.tikfetch.service.cleanup;

import dev.despical.tikfetch.config.AppProperties;
import dev.despical.tikfetch.repository.DownloadAttemptRepository;
import dev.despical.tikfetch.service.download.DownloadedVideoRetentionService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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
public class CleanupTransactionService {

    private final AppProperties properties;
    private final DownloadedVideoRetentionService retentionService;
    private final DownloadAttemptRepository attemptRepository;

    @Transactional
    public void cleanup() {
        retentionService.enforceSuccessfulRetention();

        Instant cutoff = Instant.now().minus(properties.cleanup().failedAttemptRetentionDays(), ChronoUnit.DAYS);
        attemptRepository.deleteByCreatedAtBefore(cutoff);
    }
}
