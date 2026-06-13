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
