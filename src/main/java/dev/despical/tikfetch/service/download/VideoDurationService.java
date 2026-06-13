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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@Service
public class VideoDurationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoDurationService.class);
    private static final int MAX_DEPTH = 6;

    public Long detectDurationSeconds(Path videoPath) {
        if (videoPath == null || !Files.isRegularFile(videoPath)) {
            return null;
        }

        try (RandomAccessFile file = new RandomAccessFile(videoPath.toFile(), "r")) {
            OptionalLong duration = scanBoxes(file, 0, file.length(), 0);
            return duration.isPresent() ? duration.getAsLong() : null;
        } catch (IOException exception) {
            LOGGER.warn("Could not detect video duration from '{}'", videoPath, exception);
            return null;
        }
    }

    private OptionalLong scanBoxes(RandomAccessFile file, long start, long end, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            return OptionalLong.empty();
        }

        long position = start;

        while (position + 8 <= end) {
            file.seek(position);

            long size = Integer.toUnsignedLong(file.readInt());
            String type = readType(file);
            long headerSize = 8;

            if (size == 1) {
                if (position + 16 > end) {
                    return OptionalLong.empty();
                }

                size = file.readLong();
                headerSize = 16;
            } else if (size == 0) {
                size = end - position;
            }

            if (size < headerSize || position + size > end) {
                position++;
                continue;
            }

            long contentStart = position + headerSize;
            long contentEnd = position + size;

            if ("mvhd".equals(type)) {
                OptionalLong duration = readMovieHeaderDuration(file, contentStart, contentEnd);

                if (duration.isPresent()) {
                    return duration;
                }
            }

            if (isContainer(type)) {
                OptionalLong nested = scanBoxes(file, contentStart, contentEnd, depth + 1);

                if (nested.isPresent()) {
                    return nested;
                }
            }

            position += size;
        }
        return OptionalLong.empty();
    }

    private OptionalLong readMovieHeaderDuration(RandomAccessFile file, long start, long end) throws IOException {
        if (start + 20 > end) {
            return OptionalLong.empty();
        }

        file.seek(start);

        int version = file.readUnsignedByte();
        file.skipBytes(3);

        if (version == 1) {
            if (start + 32 > end) {
                return OptionalLong.empty();
            }

            file.skipBytes(16);

            long timescale = Integer.toUnsignedLong(file.readInt());
            long duration = file.readLong();
            return toSeconds(duration, timescale);
        }

        file.skipBytes(8);

        long timescale = Integer.toUnsignedLong(file.readInt());
        long duration = Integer.toUnsignedLong(file.readInt());
        return toSeconds(duration, timescale);
    }

    private OptionalLong toSeconds(long duration, long timescale) {
        if (duration < 0 || timescale <= 0) {
            return OptionalLong.empty();
        }

        return OptionalLong.of(Math.max(1L, Math.round(duration / (double) timescale)));
    }

    private String readType(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[4];
        file.readFully(bytes);

        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private boolean isContainer(String type) {
        return switch (type) {
            case "moov", "trak", "mdia", "minf", "stbl", "edts", "udta", "meta", "ilst" -> true;
            default -> false;
        };
    }
}
