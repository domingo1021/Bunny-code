Feature: Accounts and sign-in
  As a learner
  I want an account
  So that my projects and battles are mine

  Scenario: Signing up gives me a session straight away
    When "alice" signs up with password "secret1"
    Then the response status is 201
    And the response contains an access token
    And "alice" can see their own profile including their e-mail

  Scenario: An e-mail address can only be registered once
    Given "alice" has an account
    When someone else signs up with the e-mail address of "alice"
    Then the response status is 409
    And the error code is "user.email-taken"

  Scenario Outline: Weak passwords are refused with a readable reason
    When "bob" signs up with password "<password>"
    Then the response status is 400
    And the error message mentions "6–30 characters"

    Examples:
      | password  |
      | abc1      |
      | nodigits  |
      | 1234567   |

  Scenario: Signing in with the right password
    Given "alice" has an account
    When "alice" signs in with password "secret1"
    Then the response status is 200
    And the response contains an access token

  Scenario: A wrong password is refused without revealing which part was wrong
    Given "alice" has an account
    When "alice" signs in with password "wrong-password1"
    Then the response status is 401
    And the error code is "auth.invalid-credentials"

  Scenario: Users from v1 keep their password, and it is strengthened on sign-in
    Given a v1 user "veteran" whose password "test1234" was hashed with bcrypt cost 5
    When "veteran" signs in with password "test1234"
    Then the response status is 200
    And the stored password hash of "veteran" uses bcrypt cost 12

  Scenario: My profile needs a session; public profiles do not
    Given "alice" has an account
    When an anonymous visitor requests "/api/v2/users/me"
    Then the response status is 401
    And the error code is "auth.required"
    When an anonymous visitor views the public profile of "alice"
    Then the response status is 200
    And the response has no e-mail address
