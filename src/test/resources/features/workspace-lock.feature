Feature: Exclusive editing of a version
  As a project owner
  I want only one window to edit a version at a time
  So that two windows never overwrite each other's recording

  Background:
    Given "alice" has an account
    And "bob" has an account
    And "alice" has a public project "locked_app"

  Scenario: A second window is told the version is busy and can take over
    Given "alice" is connected in window "laptop"
    And "alice" is connected in window "desktop"
    When window "laptop" asks to edit version 1 of "locked_app"
    Then window "laptop" is told "EDITING"
    When window "desktop" asks to edit version 1 of "locked_app"
    Then window "desktop" is told "LOCKED"
    When window "desktop" takes over version 1 of "locked_app"
    Then window "desktop" is told "EDITING"
    And window "laptop" is told "REVOKED"

  Scenario: Nobody can edit someone else's project
    Given "bob" is connected in window "bobs-pc"
    When window "bobs-pc" takes over version 1 of "locked_app"
    Then window "bobs-pc" is told "READ_ONLY"

  Scenario: Closing a window releases its lock
    Given "alice" is connected in window "laptop"
    And "alice" is connected in window "desktop"
    And window "laptop" asks to edit version 1 of "locked_app"
    And window "laptop" is told "EDITING"
    When window "laptop" is closed
    And window "desktop" asks to edit version 1 of "locked_app"
    Then window "desktop" is told "EDITING"

  Scenario: A recorded version is replay-only
    Given "alice" has saved a session on version 1 of "locked_app"
    And "alice" is connected in window "laptop"
    When window "laptop" asks to edit version 1 of "locked_app"
    Then window "laptop" is told "RECORDED"

  Scenario: Connecting requires a valid session
    When someone connects to the realtime API with an invalid token
    Then the connection is refused
