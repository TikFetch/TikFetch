package dev.despical.tikfetch.service;

import dev.despical.tikfetch.storage.LocalFileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Produces small JPEGs for the public card grid without changing stored media. */
@Service
public class CardThumbnailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CardThumbnailService.class);
    private static final int MAX_WIDTH = 640;
    private static final int MAX_SOURCE_DIMENSION = 4096;
    private static final long MAX_SOURCE_BYTES = 4L * 1024 * 1024;
    private static final int MAX_CACHE_ENTRIES = 32;

    private final LocalFileStorageService storageService;
    private final Map<String, byte[]> cache = new LinkedHashMap<>(MAX_CACHE_ENTRIES, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > MAX_CACHE_ENTRIES;
        }
    };

    public CardThumbnailService(LocalFileStorageService storageService) {
        this.storageService = storageService;
    }

    public Optional<byte[]> cardThumbnail(String relativePath) {
        synchronized (cache) {
            byte[] cached = cache.get(relativePath);
            if (cached != null) {
                return Optional.of(cached);
            }
        }

        Path path = storageService.resolveStoredPath(relativePath);
        try {
            long originalSize = Files.size(path);
            if (originalSize > MAX_SOURCE_BYTES) {
                return Optional.empty();
            }

            byte[] encoded = resize(path);
            if (encoded == null || encoded.length >= originalSize) {
                return Optional.empty();
            }

            synchronized (cache) {
                cache.put(relativePath, encoded);
            }
            return Optional.of(encoded);
        } catch (IOException exception) {
            LOGGER.debug("Could not resize card thumbnail {}", relativePath, exception);
            return Optional.empty();
        }
    }

    private byte[] resize(Path path) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) {
                return null;
            }
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }

            ImageReader reader = readers.next();
            BufferedImage source;
            try {
                reader.setInput(input);
                int sourceWidth = reader.getWidth(0);
                int sourceHeight = reader.getHeight(0);
                if (sourceWidth < 1 || sourceHeight < 1
                    || sourceWidth > MAX_SOURCE_DIMENSION || sourceHeight > MAX_SOURCE_DIMENSION) {
                    return null;
                }
                source = reader.read(0, (ImageReadParam) null);
            } finally {
                reader.dispose();
            }

            int width = Math.min(source.getWidth(), MAX_WIDTH);
            int height = Math.max(1, (int) Math.round((double) source.getHeight() * width / source.getWidth()));
            BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = target.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, width, height);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.drawImage(source, 0, 0, width, height, null);
            } finally {
                graphics.dispose();
            }

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
                writer.setOutput(output);
                ImageWriteParam parameters = writer.getDefaultWriteParam();
                parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parameters.setCompressionQuality(0.72f);
                writer.write(null, new IIOImage(target, null, null), parameters);
            } finally {
                writer.dispose();
            }
            return bytes.toByteArray();
        }
    }
}
