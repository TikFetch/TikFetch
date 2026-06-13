Feature: Admin JWT tokens

  Scenario: Create a parseable admin access token
    When an access token is created for admin "admin" with id 42
    Then the token subject should be "admin"

  Scenario: Hash a refresh token
    When the refresh token "refresh-token" is hashed
    Then the refresh token hash should have 64 characters
