/*
 * TikFetch - A clean web app for saving TikTok videos and photo posts.
 * Copyright (C) 2026  Berke Akçen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package dev.despical.tikfetch.service.cleanup;

import dev.despical.tikfetch.config.AppProperties;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageCleanupServiceTest {

    @Test
    void runsCleanupWhenEnabled() {
        AppProperties properties = mock(AppProperties.class);
        AppProperties.Cleanup cleanupProperties = mock(AppProperties.Cleanup.class);
        CleanupTransactionService cleanup = mock(CleanupTransactionService.class);

        when(properties.cleanup()).thenReturn(cleanupProperties);
        when(cleanupProperties.enabled()).thenReturn(true);

        new StorageCleanupService(properties, cleanup).scheduledCleanup();

        verify(cleanup).cleanup();
    }

    @Test
    void skipsCleanupWhenDisabled() {
        AppProperties properties = mock(AppProperties.class);
        AppProperties.Cleanup cleanupProperties = mock(AppProperties.Cleanup.class);
        CleanupTransactionService cleanup = mock(CleanupTransactionService.class);

        when(properties.cleanup()).thenReturn(cleanupProperties);
        when(cleanupProperties.enabled()).thenReturn(false);

        new StorageCleanupService(properties, cleanup).scheduledCleanup();

        verify(cleanup, never()).cleanup();
    }
}
