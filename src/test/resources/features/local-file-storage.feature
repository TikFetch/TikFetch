Feature: Local file storage

  Scenario: Store a downloaded video inside the configured storage root
    When a downloaded file named "video.mp4" is stored for source id "abc/123"
    Then the stored file path should start with "videos/"
    And the stored file should exist inside the storage directory

  Scenario: Store a downloaded photo inside the configured storage root
    When a downloaded file named "photo.jpg" is stored for source id "photo/123"
    Then the stored file path should start with "videos/"
    And the stored file path should end with ".jpg"
    And the stored file should exist inside the storage directory

  Scenario: Reject path traversal while resolving stored files
    When the stored path "../outside.mp4" is resolved
    Then the storage request should be rejected with a message containing "Invalid"
