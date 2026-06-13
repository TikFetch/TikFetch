package dev.despical.tikfetch.cucumber;

import dev.despical.tikfetch.entity.Admin;
import dev.despical.tikfetch.security.JwtTokenService;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

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
