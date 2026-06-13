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

import dev.despical.tikfetch.entity.Admin;
import dev.despical.tikfetch.security.JwtTokenService;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Despical
 * <p>
 * Created at 13.06.2026
 */
public class JwtSteps {

    private final JwtTokenService tokenService = new JwtTokenService(TestProperties.withStorage(Path.of("build/test-storage")));

    private String accessToken;
    private String refreshTokenHash;

    @When("an access token is created for admin {string} with id {long}")
    public void anAccessTokenIsCreatedForAdminWithId(String username, long id) {
        Admin admin = new Admin();
        admin.setId(id);
        admin.setUsername(username);

        accessToken = tokenService.createAccessToken(admin);
    }

    @Then("the token subject should be {string}")
    public void theTokenSubjectShouldBe(String expectedSubject) {
        assertThat(tokenService.parse(accessToken).getSubject()).isEqualTo(expectedSubject);
    }

    @When("the refresh token {string} is hashed")
    public void theRefreshTokenIsHashed(String refreshToken) {
        refreshTokenHash = tokenService.hashToken(refreshToken);
    }

    @Then("the refresh token hash should have {int} characters")
    public void theRefreshTokenHashShouldHaveCharacters(int expectedLength) {
        assertThat(refreshTokenHash).hasSize(expectedLength);
    }
}
