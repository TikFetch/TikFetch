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

import dev.despical.tikfetch.config.AppProperties;
import dev.despical.tikfetch.entity.DownloadStatus;
import dev.despical.tikfetch.mapper.VideoViewMapper;
import dev.despical.tikfetch.repository.DownloadedVideoRepository;
import dev.despical.tikfetch.service.download.VideoDurationService;
import dev.despical.tikfetch.storage.LocalFileStorageService;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.ProxyTransactionManagementConfiguration;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LatestVideoCacheTransactionTest {

    @Test
    void refreshesOnlyAfterTheSuccessfulDownloadTransactionCommits() {
        AppProperties properties = mock(AppProperties.class);
        DownloadedVideoRepository repository = mock(DownloadedVideoRepository.class);

        when(properties.latestVideosLimit()).thenReturn(9);
        when(repository.findByStatusOrderByDownloadedAtDesc(DownloadStatus.SUCCESS, PageRequest.of(0, 9)))
            .thenReturn(List.of());

        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(AppProperties.class, () -> properties);
            context.registerBean(DownloadedVideoRepository.class, () -> repository);
            context.registerBean(VideoViewMapper.class, () -> mock(VideoViewMapper.class));
            context.registerBean(LocalFileStorageService.class, () -> mock(LocalFileStorageService.class));
            context.registerBean(VideoDurationService.class, () -> mock(VideoDurationService.class));
            context.registerBean(LatestVideoCacheService.class);
            context.registerBean(TestTransactionManager.class);
            context.registerBean(ProxyTransactionManagementConfiguration.class);
            context.refresh();

            var transaction = new TransactionTemplate(context.getBean(TestTransactionManager.class));
            transaction.executeWithoutResult(status -> {
                context.publishEvent(new LatestVideosChangedEvent());
                verifyNoInteractions(repository);
            });

            verify(repository).findByStatusOrderByDownloadedAtDesc(
                DownloadStatus.SUCCESS,
                PageRequest.of(0, 9)
            );

            clearInvocations(repository);
            transaction.executeWithoutResult(status -> {
                context.publishEvent(new LatestVideosChangedEvent());
                status.setRollbackOnly();
            });
            verifyNoInteractions(repository);
        }
    }

    private static final class TestTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
