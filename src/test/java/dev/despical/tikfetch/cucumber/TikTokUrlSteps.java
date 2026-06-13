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

import dev.despical.tikfetch.validation.TikTokUrlValidator;
import dev.despical.tikfetch.validation.ValidatedTikTokUrl;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Despical
 * <p>
 * Created at 13.06.2026
 */
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
