Feature: Running code in the sandbox
  As a learner
  I want to run my code safely
  So that I can see what it does

  Background:
    Given "alice" has an account

  Scenario: Running a program shows what it printed
    When "alice" runs:
      """
      console.log(1 + 1)
      """
    Then the run status is "SUCCEEDED"
    And the run output is "2"

  Scenario: My own mistakes are program output, not a server error
    When "alice" runs "notDefined()"
    Then the response status is 200
    And the run status is "FAILED"
    And the run output contains "ReferenceError: notDefined is not defined"

  Scenario: Infinite loops are stopped at the time limit
    When "alice" runs "while (true) {}"
    Then the run status is "TIMED_OUT"

  Scenario: Programs that eat all the memory are stopped
    When "alice" runs "const hog = []; while (true) hog.push(new Array(1e6).fill(1));"
    Then the run status is "OUT_OF_MEMORY"

  Scenario: Shell syntax inside code never reaches a shell on the server
    When "alice" runs "console.log('$(touch /tmp/bunny-pwned)')"
    Then the run output is "$(touch /tmp/bunny-pwned)"
    And the file "/tmp/bunny-pwned" does not exist on the API host

  Scenario: Anonymous visitors cannot run code
    When an anonymous visitor runs "console.log(1)"
    Then the response status is 401
