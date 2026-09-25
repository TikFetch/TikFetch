package dev.despical.tikfetch.service;

import dev.despical.tikfetch.storage.LocalFileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CardThumbnailServiceTest {

    @TempDir
    Path directory;

    @Test
    void resizesAndCompressesCardImageWithoutChangingStoredOriginal() throws Exception {
        Path source = directory.resolve("thumbnail.jpg");
        BufferedImage image = new BufferedImage(1200, 675, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, random.nextInt(1 << 24));
            }
        }
        ImageIO.write(image, "jpeg", source.toFile());
        long originalSize = Files.size(source);

        LocalFileStorageService storage = mock(LocalFileStorageService.class);
        when(storage.resolveStoredPath("thumbnails/thumbnail.jpg")).thenReturn(source);
        CardThumbnailService service = new CardThumbnailService(storage);

        byte[] card = service.cardThumbnail("thumbnails/thumbnail.jpg").orElseThrow();
        BufferedImage resized = ImageIO.read(new java.io.ByteArrayInputStream(card));

        assertThat(resized.getWidth()).isEqualTo(640);
        assertThat(resized.getHeight()).isEqualTo(360);
        assertThat((long) card.length).isLessThan(originalSize);
        assertThat(Files.size(source)).isEqualTo(originalSize);
        assertThat(service.cardThumbnail("thumbnails/thumbnail.jpg").orElseThrow()).isSameAs(card);
    }
}
