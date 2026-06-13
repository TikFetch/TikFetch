package dev.despical.tikfetch.repository;

import dev.despical.tikfetch.entity.DownloadStatus;
import dev.despical.tikfetch.entity.DownloadedVideo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
public interface DownloadedVideoRepository extends JpaRepository<DownloadedVideo, Long> {

    Optional<DownloadedVideo> findFirstByNormalizedUrlAndStatusOrderByDownloadedAtDesc(String normalizedUrl, DownloadStatus status);

    List<DownloadedVideo> findByStatusOrderByDownloadedAtDesc(DownloadStatus status, Pageable pageable);

    long countByStatus(DownloadStatus status);

    @Query("""
        SELECT video FROM DownloadedVideo video
        WHERE video.status = dev.despical.tikfetch.entity.DownloadStatus.SUCCESS
        ORDER BY video.downloadedAt DESC, video.id DESC
        """)
    List<DownloadedVideo> findSuccessfulForRetention(Pageable pageable);
}
