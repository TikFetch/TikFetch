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

package dev.despical.tikfetch.mapper;

import dev.despical.tikfetch.dto.AdminVideoView;
import dev.despical.tikfetch.dto.AttemptView;
import dev.despical.tikfetch.dto.LatestVideoView;
import dev.despical.tikfetch.entity.DownloadAttempt;
import dev.despical.tikfetch.entity.DownloadedVideo;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@Mapper(componentModel = "spring")
public interface VideoViewMapper {

    DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    @Mapping(target = "thumbnailUrl", expression = "java(thumbnailUrl(video))")
    @Mapping(target = "streamUrl", expression = "java(streamUrl(video))")
    @Mapping(target = "audioDownloadUrl", expression = "java(audioDownloadUrl(video))")
    @Mapping(target = "image", expression = "java(isImage(video))")
    @Mapping(target = "duration", expression = "java(formatDuration(video.getDurationSeconds(), isImage(video)))")
    @Mapping(target = "fileSize", expression = "java(humanReadableByteCount(video.getFileSize()))")
    @Mapping(target = "likeCount", expression = "java(compactCount(video.getLikeCount()))")
    @Mapping(target = "commentCount", expression = "java(compactCount(video.getCommentCount()))")
    LatestVideoView toLatestView(DownloadedVideo video);

    @Mapping(target = "duration", expression = "java(formatDuration(video.getDurationSeconds()))")
    @Mapping(target = "fileSize", expression = "java(humanReadableByteCount(video.getFileSize()))")
    AdminVideoView toAdminView(DownloadedVideo video);

    AttemptView toAttemptView(DownloadAttempt attempt);

    default String thumbnailUrl(DownloadedVideo video) {
        if (video.getThumbnailPath() != null) {
            return "/media/thumbnails/" + video.getId();
        }

        return isImage(video) ? streamUrl(video) : null;
    }

    default String streamUrl(DownloadedVideo video) {
        return "/media/stream/" + video.getId();
    }

    default String audioDownloadUrl(DownloadedVideo video) {
        return video.getAudioPath() == null ? null : "/media/audio/" + video.getId();
    }

    default boolean isImage(DownloadedVideo video) {
        return video.getMimeType() != null && video.getMimeType().toLowerCase().startsWith("image/");
    }

    default String format(Instant instant) {
        return instant == null ? "Not available" : FORMATTER.format(instant);
    }

    default String humanReadableByteCount(Long bytes) {
        if (bytes == null) {
            return "Unknown";
        }

        long absolute = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);

        if (absolute < 1024) {
            return bytes + " B";
        }

        CharacterIterator iterator = new StringCharacterIterator("KMGTPE");
        long value = absolute;

        for (int i = 40; i >= 0 && absolute > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            iterator.next();
        }

        value *= Long.signum(bytes);
        return "%.1f %cB".formatted(value / 1024.0, iterator.current());
    }

    default String formatDuration(Long seconds) {
        return formatDuration(seconds, false);
    }

    default String formatDuration(Long seconds, boolean image) {
        if (image) {
            return "Photo";
        }

        if (seconds == null || seconds < 0) {
            return "Unknown length";
        }

        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        return "%d:%02d".formatted(minutes, remainingSeconds);
    }

    default String compactCount(Long count) {
        if (count == null) {
            return "Unknown";
        }

        if (count < 1000) {
            return count.toString();
        }

        if (count < 1_000_000) {
            return "%.1fK".formatted(count / 1000.0);
        }

        return "%.1fM".formatted(count / 1_000_000.0);
    }
}
