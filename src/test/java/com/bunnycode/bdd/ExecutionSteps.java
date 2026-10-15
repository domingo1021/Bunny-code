package com.bunnycode.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class ExecutionSteps {

    private final World world;
    private final ApiClient api;

    public ExecutionSteps(World world, ApiClient api) {
        this.world = world;
        this.api = api;
    }

    @When("{string} runs:")
    public void runsDocString(String user, String code) {
        runs(user, code);
    }

    @When("{string} runs {string}")
    public void runs(String user, String code) {
        world.lastResponse(api.post("/api/v2/executions", world.tokenOf(user), Map.of("code", code)));
    }

    @When("an anonymous visitor runs {string}")
    public void anonymousRuns(String code) {
        world.lastResponse(api.post("/api/v2/executions", null, Map.of("code", code)));
    }

    @Then("the run status is {string}")
    public void runStatus(String status) {
        assertThat(world.lastResponse().status()).as(world.lastResponse().body().toString()).isEqualTo(200);
        assertThat(world.lastResponse().body().path("status").asString()).isEqualTo(status);
    }

    @Then("the run output is {string}")
    public void runOutput(String output) {
        assertThat(world.lastResponse().body().path("output").asString().strip()).isEqualTo(output);
    }

    @Then("the run output contains {string}")
    public void runOutputContains(String text) {
        assertThat(world.lastResponse().body().path("output").asString()).contains(text);
    }

    @Then("the file {string} does not exist on the API host")
    public void fileDoesNotExist(String path) {
        assertThat(Files.exists(Path.of(path))).isFalse();
    }
}
