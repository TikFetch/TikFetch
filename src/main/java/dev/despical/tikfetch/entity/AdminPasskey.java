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

package dev.despical.tikfetch.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author Despical
 * <p>
 * Created at 13.06.2026
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "admin_passkeys")
public class AdminPasskey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private Admin admin;

    @Column(nullable = false, length = 120)
    private String label;

    @Lob
    @Column(nullable = false, name = "credential_id", columnDefinition = "BLOB")
    private byte[] credentialId;

    @Lob
    @Column(nullable = false, name = "user_handle", columnDefinition = "BLOB")
    private byte[] userHandle;

    @Lob
    @Column(nullable = false, name = "public_key_cose", columnDefinition = "BLOB")
    private byte[] publicKeyCose;

    @Column(nullable = false, name = "signature_count")
    private long signatureCount;

    @Column(name = "backup_eligible")
    private Boolean backupEligible;

    @Column(name = "backed_up")
    private Boolean backedUp;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(nullable = false, name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
