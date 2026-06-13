package dev.despical.tikfetch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author Despical
 * <p>
 * Created at 12.06.2026
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "download_attempts")
public class DownloadAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, name = "submitted_url", length = 2048)
    private String submittedUrl;

    @Column(name = "normalized_url", length = 2048)
    private String normalizedUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DownloadStatus status;

    @Column(name = "message", length = 1024)
    private String message;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(nullable = false, name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
