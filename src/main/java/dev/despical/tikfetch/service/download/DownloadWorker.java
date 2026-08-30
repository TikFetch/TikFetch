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

import dev.despical.tikfetch.exception.UserFacingException;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * @author Despical
 * <p>
 * Created at 30.08.2026
 */
@Component
@RequiredArgsConstructor
public class DownloadWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadWorker.class);

    private final DownloadCoordinator downloadCoordinator;

    @Async("downloadExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void process(DownloadRequestedEvent event) {
        try {
            downloadCoordinator.process(event);
        } catch (UserFacingException exception) {
            LOGGER.info("Download {} failed: {}", event.videoId(), exception.getMessage());
        } catch (RuntimeException exception) {
            LOGGER.error("Download {} failed unexpectedly", event.videoId(), exception);
        }
    }
}
