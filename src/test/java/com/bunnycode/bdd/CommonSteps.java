package com.bunnycode.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class CommonSteps {

    private final World world;
    private final ApiClient api;

    public CommonSteps(World world, ApiClient api) {
        this.world = world;
        this.api = api;
    }

    @When("an anonymous visitor requests {string}")
    public void anonymousRequest(String path) {
        world.lastResponse(api.get(path, null));
    }

    @Then("the response status is {int}")
    public void responseStatus(int status) {
        assertThat(world.lastResponse().status()).as("status of %s", world.lastResponse().body()).isEqualTo(status);
    }

    @Then("the error code is {string}")
    public void errorCode(String code) {
        assertThat(world.lastResponse().body().path("code").asString()).isEqualTo(code);
    }

    @Then("the error message mentions {string}")
    public void errorMessageMentions(String text) {
        assertThat(world.lastResponse().body().path("detail").asString()).contains(text);
    }

    @Then("the response field {string} is {long}")
    public void responseField(String field, long value) {
        assertThat(world.lastResponse().body().path(field).asLong()).isEqualTo(value);
    }

    /** Feature files write line breaks as \n inside tables and strings. */
    static String unescape(String text) {
        return text.replace("\\n", "\n");
    }
}
