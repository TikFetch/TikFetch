Feature: TikTok URL validation

  Scenario: Normalize a TikTok video URL
    When the user validates the TikTok URL "https://www.tiktok.com/@creator/video/123?lang=en"
    Then the original URL should contain "?lang=en"
    And the normalized URL should be "https://www.tiktok.com/@creator/video/123"
    And the media kind should be "VIDEO"

  Scenario: Normalize a TikTok photo URL
    When the user validates the TikTok URL "https://www.tiktok.com/@creator/photo/7644218109468970261?lang=en"
    Then the normalized URL should be "https://www.tiktok.com/@creator/photo/7644218109468970261"
    And the media kind should be "PHOTO"

  Scenario: Normalize a TikTok photo URL with tracking parameters
    When the user validates the TikTok URL "https://www.tiktok.com/@dertamoyl/photo/7650483855962410261?_r=1&_t=ZS-97K9WpMPKKq"
    Then the normalized URL should be "https://www.tiktok.com/@dertamoyl/photo/7650483855962410261"
    And the media kind should be "PHOTO"

  Scenario: Accept a short TikTok URL
    When the user validates the TikTok URL "https://vm.tiktok.com/abc123"
    Then the normalized URL should be "https://vm.tiktok.com/abc123"
    And the media kind should be "SHORT"

  Scenario: Accept a mobile share TikTok short URL
    When the user validates the TikTok URL "https://vt.tiktok.com/ZSQWFjSKx/"
    Then the normalized URL should be "https://vt.tiktok.com/ZSQWFjSKx/"
    And the media kind should be "SHORT"

  Scenario: Reject a non-TikTok URL
    When the user validates the TikTok URL "https://example.com/video/123"
    Then the URL should be rejected with a message containing "Only TikTok"

  Scenario: Reject malformed input
    When the user validates the TikTok URL "not a url"
    Then the URL should be rejected with a message containing "valid TikTok URL"
