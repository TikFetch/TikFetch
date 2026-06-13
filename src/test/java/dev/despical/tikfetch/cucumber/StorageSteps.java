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

package dev.despical.tikfetch.cucumber;

import dev.despical.tikfetch.exception.UserFacingException;
import dev.despical.tikfetch.storage.LocalFileStorageService;
import dev.despical.tikfetch.storage.StoredFile;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

public class StorageSteps {

    private Path storageDirectory;
    private LocalFileStorageService storageService;
    private StoredFile storedFile;
    private Exception thrownException;

    @Before
    public void setUpStorage() throws IOException {
        storageDirectory = Files.createTempDirectory("tikfetch-cucumber-storage-");
        storageService = new LocalFileStorageService(TestProperties.withStorage(storageDirectory));
    }

    @After
    public void cleanUpStorage() throws IOException {
        if (storageDirectory == null || !Files.exists(storageDirectory)) {
            return;
        }

        try (var paths = Files.walk(storageDirectory)) {
            paths.sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException _) {
                    }
                });
        }
    }

    @When("a downloaded file named {string} is stored for source id {string}")
    public void aDownloadedFileNamedIsStoredForSourceId(String fileName, String sourceId) throws IOException {
        Path sourceFile = Files.createTempFile("tikfetch-source-", "-" + fileName);
        Files.writeString(sourceFile, "media");

        storedFile = storageService.storeVideo(sourceFile, sourceId);
    }

    @Then("the stored file path should start with {string}")
    public void theStoredFilePathShouldStartWith(String expectedPrefix) {
        assertThat(storedFile.relativePath()).startsWith(expectedPrefix);
    }

    @Then("the stored file path should end with {string}")
    public void theStoredFilePathShouldEndWith(String expectedSuffix) {
        assertThat(storedFile.relativePath()).endsWith(expectedSuffix);
    }

    @Then("the stored file should exist inside the storage directory")
    public void theStoredFileShouldExistInsideTheStorageDirectory() {
        Path storedPath = storageService.resolveStoredPath(storedFile.relativePath());

        assertThat(storedPath).exists();
        assertThat(storedPath).startsWith(storageDirectory);
    }

    @When("the stored path {string} is resolved")
    public void theStoredPathIsResolved(String relativePath) {
        try {
            storageService.resolveStoredPath(relativePath);
            thrownException = null;
        } catch (Exception exception) {
            thrownException = exception;
        }
    }

    @Then("the storage request should be rejected with a message containing {string}")
    public void theStorageRequestShouldBeRejectedWithAMessageContaining(String expectedText) {
        assertThat(thrownException).isInstanceOf(UserFacingException.class);
        assertThat(thrownException).hasMessageContaining(expectedText);
    }
}
