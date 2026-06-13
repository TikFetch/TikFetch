package dev.despical.tikfetch.cucumber;

import dev.despical.tikfetch.validation.TikTokUrlValidator;
import dev.despical.tikfetch.validation.ValidatedTikTokUrl;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

public class TikTokUrlSteps {

    private final TikTokUrlValidator validator = new TikTokUrlValidator();

    private ValidatedTikTokUrl validatedUrl;
    private Exception thrownException;

    @When("the user validates the TikTok URL {string}")
    public void theUserValidatesTheTikTokUrl(String url) {
        try {
            validatedUrl = validator.validateAndNormalize(url);
            thrownException = null;
        } catch (Exception exception) {
            validatedUrl = null;
            thrownException = exception;
        }
    }

    @Then("the normalized URL should be {string}")
    public void theNormalizedUrlShouldBe(String expectedUrl) {
        assertThat(validatedUrl.normalizedUrl()).isEqualTo(expectedUrl);
    }

    @Then("the original URL should contain {string}")
    public void theOriginalUrlShouldContain(String expectedText) {
        assertThat(validatedUrl.originalUrl()).contains(expectedText);
    }

    @Then("the media kind should be {string}")
    public void theMediaKindShouldBe(String expectedKind) {
        assertThat(validatedUrl.mediaKind()).isEqualTo(ValidatedTikTokUrl.MediaKind.valueOf(expectedKind));
    }

    @Then("the URL should be rejected with a message containing {string}")
    public void theUrlShouldBeRejectedWithAMessageContaining(String expectedText) {
        assertThat(thrownException).isInstanceOf(IllegalArgumentException.class);
        assertThat(thrownException).hasMessageContaining(expectedText);
    }
}
