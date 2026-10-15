Feature: Battle Valley
  As a learner
  I want to race someone on a coding question while others watch
  So that practising is fun

  Background:
    Given "alice" has an account
    And "bob" has an account
    And "carol" has an account
    And "alice", "bob" and "carol" are online

  Scenario: An invitation is broadcast and the first taker gets the battle
    When "alice" invites everyone to a "EASY" battle called "duel"
    Then "bob" and "carol" see an invitation to "duel" from "alice"
    When "bob" accepts the invitation to "duel"
    Then "alice" and "bob" are sent to the battle "duel"
    When "carol" accepts the invitation to "duel"
    Then "carol" is told "battle.invitation-expired"

  Scenario: Nobody can accept their own invitation
    When "alice" invites everyone to a "EASY" battle called "solo"
    And "alice" accepts the invitation to "solo"
    Then "alice" is told "battle.self-invite"

  Scenario: Inviting again within seconds is throttled
    When "alice" invites everyone to a "EASY" battle called "first"
    And "alice" invites everyone to a "EASY" battle called "second"
    Then "alice" is told "battle.invite-throttled"

  Scenario: A full battle — ready, a wrong answer, then a winner
    Given "alice" and "bob" are in a "EASY" battle called "showdown"
    And "carol" is watching "showdown"
    When "alice" and "bob" get ready in "showdown"
    Then everyone in "showdown" sees it start exactly once
    When "bob" submits in "showdown":
      """
      function helloWorld(s) { return s.toUpperCase(); }
      """
    Then everyone in "showdown" sees "bob" judged "WRONG_ANSWER" with 2 submissions left
    And the failing test case shown to "carol" in "showdown" is visible with expected output '"Hello world !"'
    When "alice" submits in "showdown":
      """
      function helloWorld(s) { return s; }
      """
    Then everyone in "showdown" sees "alice" judged "ACCEPTED" with 2 submissions left
    And everyone in "showdown" sees "alice" win
    And the battle "showdown" is "FINISHED" with "alice" as the winner
    And the winning code of "showdown" is archived
    And "showdown" has been watched 1 time

  Scenario: Spectators see the players type live
    Given "alice" and "bob" are in a "EASY" battle called "live"
    And "carol" is watching "live"
    When "alice" types "function helloWorld" in "live"
    Then "carol" sees the code of "alice" in "live" become "function helloWorld"

  Scenario: Leaving a running battle forfeits it
    Given "alice" and "bob" are in a "EASY" battle called "quitter"
    And "alice" and "bob" get ready in "quitter"
    When "bob" leaves "quitter"
    Then everyone in "quitter" sees it terminated because "bob left the battle."

  Scenario: Running out of submissions ends the battle
    Given "alice" and "bob" are in a "EASY" battle called "stubborn"
    And "alice" and "bob" get ready in "stubborn"
    When "alice" submits a wrong answer 3 times in "stubborn"
    Then everyone in "stubborn" sees it terminated because "alice ran out of submissions."

  Scenario: Spectators cannot play
    Given "alice" and "bob" are in a "EASY" battle called "watched"
    And "carol" is watching "watched"
    When "carol" tries to get ready in "watched"
    Then "carol" is told "battle.not-player"

  Scenario: Hidden test cases stay hidden
    Given "alice" and "bob" are in a "MEDIUM" battle called "secret_cases"
    And "alice" and "bob" get ready in "secret_cases"
    When "alice" submits in "secret_cases":
      """
      function twoSum(nums, target) {
        if (nums.length === 1) return [42];
        for (let i = 0; i < nums.length; i++)
          for (let j = i + 1; j < nums.length; j++)
            if (nums[i] + nums[j] === target) return [i, j];
        return [];
      }
      """
    Then everyone in "secret_cases" sees "alice" judged "WRONG_ANSWER" with 2 submissions left
    And the failing test case shown to "alice" in "secret_cases" is hidden

  Scenario: The lobby filters battles by status and difficulty
    Given "alice" and "bob" are in a "EASY" battle called "easy_one"
    And "bob" and "carol" are in a "MEDIUM" battle called "medium_one"
    And "alice" and "bob" get ready in "easy_one"
    When an anonymous visitor requests "/api/v2/battles?status=RUNNING"
    Then the lobby lists "easy_one"
    When an anonymous visitor requests "/api/v2/battles?level=MEDIUM"
    Then the lobby lists "medium_one"
    When an anonymous visitor requests "/api/v2/battles"
    Then the lobby lists "medium_one, easy_one" in any order
