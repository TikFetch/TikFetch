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

package dev.despical.tikfetch.util;

import lombok.experimental.UtilityClass;

import java.nio.file.Path;
import java.util.Locale;

/**
 * @author Despical
 * <p>
 * Created at 13.06.2026
 */
@UtilityClass
public class FileUtils {

    public static String extensionOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');

        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }

        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
