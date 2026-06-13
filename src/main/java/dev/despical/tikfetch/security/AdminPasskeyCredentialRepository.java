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

package dev.despical.tikfetch.security;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import dev.despical.tikfetch.entity.AdminPasskey;
import dev.despical.tikfetch.repository.AdminPasskeyRepository;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * @author Despical
 * <p>
 * Created at 13.06.2026
 */
@Component
@RequiredArgsConstructor
public class AdminPasskeyCredentialRepository implements CredentialRepository {

    private final AdminPasskeyRepository passkeyRepository;

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return Set.of();
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return Optional.empty();
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return passkeyRepository.findAllByUserHandle(userHandle.getBytes())
            .stream()
            .findFirst()
            .map(passkey -> passkey.getAdmin().getUsername());
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        return passkeyRepository.findByCredentialIdAndUserHandle(credentialId.getBytes(), userHandle.getBytes())
            .map(this::toRegisteredCredential);
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        return passkeyRepository.findByCredentialId(credentialId.getBytes())
            .map(this::toRegisteredCredential)
            .map(Set::of)
            .orElseGet(Set::of);
    }

    private RegisteredCredential toRegisteredCredential(AdminPasskey passkey) {
        return RegisteredCredential.builder()
            .credentialId(new ByteArray(passkey.getCredentialId()))
            .userHandle(new ByteArray(passkey.getUserHandle()))
            .publicKeyCose(new ByteArray(passkey.getPublicKeyCose()))
            .signatureCount(passkey.getSignatureCount())
            .backupEligible(passkey.getBackupEligible())
            .backupState(passkey.getBackedUp())
            .build();
    }

    static byte[] userHandleFor(long adminId) {
        return ("admin:" + adminId).getBytes(StandardCharsets.UTF_8);
    }
}
