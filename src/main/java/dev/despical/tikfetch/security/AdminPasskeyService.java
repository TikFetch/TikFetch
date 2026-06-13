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

import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import dev.despical.tikfetch.config.AppProperties;
import dev.despical.tikfetch.entity.Admin;
import dev.despical.tikfetch.entity.AdminPasskey;
import dev.despical.tikfetch.repository.AdminPasskeyRepository;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminPasskeyService {

    private final AppProperties properties;
    private final AdminPasskeyRepository passkeyRepository;
    private final AdminPasskeyCredentialRepository credentialRepository;

    public PublicKeyCredentialCreationOptions startRegistration(Admin admin) {
        byte[] userHandle = AdminPasskeyCredentialRepository.userHandleFor(admin.getId());
        UserIdentity userIdentity = UserIdentity.builder()
            .name(admin.getUsername())
            .displayName(admin.getUsername())
            .id(new ByteArray(userHandle))
            .build();

        AuthenticatorSelectionCriteria selectionCriteria = AuthenticatorSelectionCriteria.builder()
            .residentKey(ResidentKeyRequirement.REQUIRED)
            .userVerification(UserVerificationRequirement.REQUIRED)
            .build();

        return relyingParty().startRegistration(StartRegistrationOptions.builder()
            .user(userIdentity)
            .authenticatorSelection(selectionCriteria)
            .timeout(120_000)
            .build());
    }

    public AssertionRequest startLogin() {
        return relyingParty().startAssertion(StartAssertionOptions.builder()
            .userVerification(UserVerificationRequirement.REQUIRED)
            .timeout(120_000)
            .build());
    }

    @Transactional
    public AdminPasskey finishRegistration(Admin admin, String requestJson, String credentialJson, String label) {
        try {
            PublicKeyCredentialCreationOptions request = PublicKeyCredentialCreationOptions.fromJson(requestJson);
            var response = PublicKeyCredential.parseRegistrationResponseJson(credentialJson);
            RegistrationResult result = relyingParty().finishRegistration(FinishRegistrationOptions.builder()
                .request(request)
                .response(response)
                .build());

            AdminPasskey passkey = new AdminPasskey();
            passkey.setAdmin(admin);
            passkey.setLabel(normalizeLabel(label));
            passkey.setCredentialId(result.getKeyId().getId().getBytes());
            passkey.setUserHandle(AdminPasskeyCredentialRepository.userHandleFor(admin.getId()));
            passkey.setPublicKeyCose(result.getPublicKeyCose().getBytes());
            passkey.setSignatureCount(result.getSignatureCount());
            passkey.setBackupEligible(result.isBackupEligible());
            passkey.setBackedUp(result.isBackedUp());

            return passkeyRepository.save(passkey);
        } catch (IOException | RegistrationFailedException exception) {
            throw new BadCredentialsException("Could not register this passkey.", exception);
        }
    }

    @Transactional
    public Admin finishLogin(String requestJson, String credentialJson) {
        try {
            AssertionRequest request = AssertionRequest.fromJson(requestJson);
            var response = PublicKeyCredential.parseAssertionResponseJson(credentialJson);
            AssertionResult result = relyingParty().finishAssertion(FinishAssertionOptions.builder()
                .request(request)
                .response(response)
                .build());

            if (!result.isSuccess()) {
                throw new BadCredentialsException("Passkey verification failed.");
            }

            AdminPasskey passkey = passkeyRepository.findByCredentialId(result.getCredentialId().getBytes())
                .orElseThrow(() -> new BadCredentialsException("Passkey is not registered."));
            passkey.setSignatureCount(result.getSignatureCount());
            passkey.setBackupEligible(result.isBackupEligible());
            passkey.setBackedUp(result.isBackedUp());
            passkey.setLastUsedAt(Instant.now());
            passkeyRepository.save(passkey);

            Admin admin = passkey.getAdmin();
            if (!admin.isEnabled()) {
                throw new BadCredentialsException("Admin account is disabled.");
            }

            return admin;
        } catch (IOException | AssertionFailedException exception) {
            throw new BadCredentialsException("Passkey verification failed.", exception);
        }
    }

    public List<AdminPasskey> list(Admin admin) {
        return passkeyRepository.findAllByAdminOrderByCreatedAtDesc(admin);
    }

    @Transactional
    public void delete(Admin admin, Long id) {
        passkeyRepository.deleteByIdAndAdmin(id, admin);
    }

    private RelyingParty relyingParty() {
        URI baseUri = URI.create(properties.baseUrl());
        String origin = baseUri.getScheme() + "://" + baseUri.getAuthority();
        String host = baseUri.getHost() == null ? "localhost" : baseUri.getHost();

        return RelyingParty.builder()
            .identity(RelyingPartyIdentity.builder()
                .id(host)
                .name(properties.name())
                .build())
            .credentialRepository(credentialRepository)
            .origins(java.util.Set.of(origin))
            .allowOriginPort(true)
            .allowUntrustedAttestation(true)
            .build();
    }

    private String normalizeLabel(String label) {
        if (label == null || label.isBlank()) {
            return "Passkey";
        }

        String trimmed = label.trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
    }
}
