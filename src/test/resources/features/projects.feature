Feature: Projects and versions
  As a learner
  I want to keep my code in projects with versions
  So that I can share it and see how it evolved

  Background:
    Given "alice" has an account
    And "bob" has an account

  Scenario: A new project starts with version 1 holding the template file
    When "alice" creates a public project "bunny_app"
    Then the response status is 201
    And the project "bunny_app" has versions "main" numbered "1"

  Scenario: Project names are unique across all users
    Given "alice" has a public project "bunny_app"
    When "bob" creates a public project "bunny_app"
    Then the response status is 409
    And the error code is "project.name-taken"

  Scenario: Private projects exist only for their owner
    Given "alice" has a private project "secret_app"
    Then "bob" gets 404 for the project "secret_app"
    And an anonymous visitor gets 404 for the project "secret_app"
    And "alice" can open the project "secret_app"
    And the public feed does not list "secret_app"
    And "bob" sees 0 projects on the profile of "alice"
    And "alice" sees 1 project on the profile of "alice"

  Scenario: Search matches project name, description or owner name
    Given "alice" has a public project "rabbit_hole"
    And "bob" has a public project "carrots"
    When an anonymous visitor searches projects for "bob"
    Then the search results are "carrots"
    When an anonymous visitor searches projects for "rabbit"
    Then the search results are "rabbit_hole"

  Scenario: Only the owner branches versions, which are numbered in order
    Given "alice" has a public project "bunny_app"
    When "alice" creates version "feature" with file "feature.js" in "bunny_app"
    Then the response status is 201
    And the project "bunny_app" has versions "main, feature" numbered "1, 2"
    When "bob" creates version "hijack" with file "x.js" in "bunny_app"
    Then the response status is 403
    When "alice" creates version "feature" with file "again.js" in "bunny_app"
    Then the response status is 409
    And the error code is "version.name-taken"

  Scenario: A star counts once per user
    Given "alice" has a public project "bunny_app"
    When "bob" stars "bunny_app" 3 times
    Then "bunny_app" has 1 star
    And "bob" sees that they starred "bunny_app"
    When "bob" unstars "bunny_app"
    Then "bunny_app" has 0 stars

  Scenario: Views count once per viewer and never for the owner
    Given "alice" has a public project "bunny_app"
    When "bob" views "bunny_app" 3 times
    And "alice" views "bunny_app" 2 times
    Then "bunny_app" has been viewed 1 time

  Scenario: The most popular projects are ranked by views
    Given "alice" has public projects "a1, a2, a3, a4"
    And "bob" views "a3" 1 time
    When an anonymous visitor asks for the 3 most popular projects
    Then the first popular project is "a3"

  Scenario: The home page headline counts public projects, their recordings and listed battles
    Given "alice" has public projects "a1, a2"
    And "alice" has a private project "hidden_one"
    And "alice" has saved a session on version 1 of "a1"
    And "alice" has saved a session on version 1 of "hidden_one"
    When an anonymous visitor requests "/api/v2/stats/projects"
    Then the response status is 200
    And the response field "projects" is 2
    And the response field "recordings" is 1
    When an anonymous visitor requests "/api/v2/stats/battles"
    Then the response field "battles" is 0
