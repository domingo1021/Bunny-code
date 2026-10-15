Feature: Recording and replaying a coding session
  As a learner
  I want my coding process recorded
  So that others can replay how the code was written

  Background:
    Given "alice" has an account
    And "bob" has an account
    And "alice" has a public project "replay_me"

  Scenario: Saving a session stores the final code and makes the version replay-only
    When "alice" saves this session on version 1 of "replay_me":
      | action | line | index | code |
      | create | 0    | 0     | h    |
      | create | 0    | 1     | i    |
      | enter  | 1    | 0     | \n   |
    Then the response status is 201
    And the current file of version 1 of "replay_me" contains exactly "hi\n"
    And version 1 of "replay_me" has a recording
    When an anonymous visitor replays version 1 of "replay_me"
    Then the response status is 200
    And the replay format is "V2"
    And the replay events are, in order:
      | action | line | index | code |
      | create | 0    | 0     | h    |
      | create | 0    | 1     | i    |
      | enter  | 1    | 0     | \n   |

  Scenario: A version can only be recorded once
    Given "alice" has saved a session on version 1 of "replay_me"
    When "alice" saves this session on version 1 of "replay_me":
      | action | line | index | code |
      | create | 0    | 0     | x    |
    Then the response status is 409
    And the error code is "recording.exists"

  Scenario: Only the owner can record
    When "bob" saves this session on version 1 of "replay_me":
      | action | line | index | code |
      | create | 0    | 0     | x    |
    Then the response status is 403

  Scenario: Replays of private projects stay private
    Given "alice" has a private project "hidden_replay"
    And "alice" has saved a session on version 1 of "hidden_replay"
    When "bob" replays version 1 of "hidden_replay"
    Then the response status is 404

  Scenario: Sessions recorded by v1 still replay
    Given version 1 of "replay_me" has a v1 recording with a "create" of "x" at line 2 index 5
    When an anonymous visitor replays version 1 of "replay_me"
    Then the replay format is "LEGACY"
    And the replay events are, in order:
      | action | line | index | code |
      | create | 2    | 5     | x    |
