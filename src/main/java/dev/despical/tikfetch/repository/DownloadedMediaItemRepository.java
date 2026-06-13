package dev.despical.tikfetch.repository;

import dev.despical.tikfetch.entity.DownloadedMediaItem;
import dev.despical.tikfetch.entity.DownloadedVideo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
public interface DownloadedMediaItemRepository extends JpaRepository<DownloadedMediaItem, Long> {

    List<DownloadedMediaItem> findByVideoOrderByPositionIndexAsc(DownloadedVideo video);

    Optional<DownloadedMediaItem> findByVideoAndPositionIndex(DownloadedVideo video, Integer positionIndex);

    void deleteByVideo(DownloadedVideo video);
}
