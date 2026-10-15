package com.bunnycode.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import com.bunnycode.bdd.ApiClient.ApiResponse;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import tools.jackson.databind.JsonNode;

public class ProjectSteps {

    private final World world;
    private final ApiClient api;

    public ProjectSteps(World world, ApiClient api) {
        this.world = world;
        this.api = api;
    }

    @When("{string} creates a {word} project {string}")
    public void createProject(String owner, String visibility, String project) {
        world.lastResponse(api.post("/api/v2/projects", world.tokenOf(owner), Map.of(
                "name", project,
                "description", "About " + project,
                "visibility", visibility.toUpperCase(),
                "versionName", "main",
                "fileName", "main.js")));
    }

    @Given("{string} has a {word} project {string}")
    public void hasProject(String owner, String visibility, String project) {
        createProject(owner, visibility, project);
        assertThat(world.lastResponse().status()).as(world.lastResponse().body().toString()).isEqualTo(201);
    }

    @Given("{string} has public projects {string}")
    public void hasProjects(String owner, String projects) {
        split(projects).forEach(project -> hasProject(owner, "public", project));
    }

    @Then("the project {string} has versions {string} numbered {string}")
    public void versions(String project, String names, String numbers) {
        JsonNode versions = api.get("/api/v2/projects/" + project, null).body().path("versions");
        assertThat(stream(versions).map(version -> version.path("name").asString()).toList()).isEqualTo(split(names));
        assertThat(stream(versions).map(version -> String.valueOf(version.path("number").asInt())).toList())
                .isEqualTo(split(numbers));
        assertThat(stream(versions)).allSatisfy(version -> assertThat(version.path("file").path("url").asString()).isNotBlank());
    }

    @Then("{string} gets {int} for the project {string}")
    public void userGetsStatus(String user, int status, String project) {
        assertThat(api.get("/api/v2/projects/" + project, world.tokenOf(user)).status()).isEqualTo(status);
    }

    @Then("an anonymous visitor gets {int} for the project {string}")
    public void anonymousGetsStatus(int status, String project) {
        assertThat(api.get("/api/v2/projects/" + project, null).status()).isEqualTo(status);
    }

    @Then("{string} can open the project {string}")
    public void canOpen(String user, String project) {
        userGetsStatus(user, 200, project);
    }

    @Then("the public feed does not list {string}")
    public void feedDoesNotList(String project) {
        JsonNode feed = api.get("/api/v2/projects?size=50", null).body().path("content");
        assertThat(stream(feed).map(summary -> summary.path("name").asString())).doesNotContain(project);
    }

    @Then("{string} sees {int} project(s) on the profile of {string}")
    public void profileProjects(String viewer, int count, String owner) {
        ApiResponse response = api.get("/api/v2/users/" + world.account(owner).id() + "/projects", world.tokenOf(viewer));
        assertThat(response.body().path("totalElements").asInt()).isEqualTo(count);
    }

    @When("an anonymous visitor searches projects for {string}")
    public void search(String keyword) {
        world.lastResponse(api.get("/api/v2/projects?keyword=" + keyword, null));
    }

    @Then("the search results are {string}")
    public void searchResults(String names) {
        assertThat(stream(world.lastResponse().body().path("content")).map(summary -> summary.path("name").asString()).toList())
                .isEqualTo(split(names));
    }

    @When("{string} creates version {string} with file {string} in {string}")
    public void createVersion(String user, String version, String file, String project) {
        world.lastResponse(api.post("/api/v2/projects/" + project + "/versions", world.tokenOf(user),
                Map.of("versionName", version, "fileName", file)));
    }

    @When("{string} stars {string} {int} time(s)")
    public void star(String user, String project, int times) {
        for (int i = 0; i < times; i++) {
            assertThat(api.put("/api/v2/projects/" + project + "/star", world.tokenOf(user)).status()).isEqualTo(204);
        }
    }

    @When("{string} unstars {string}")
    public void unstar(String user, String project) {
        assertThat(api.delete("/api/v2/projects/" + project + "/star", world.tokenOf(user)).status()).isEqualTo(204);
    }

    @Then("{string} has {int} star(s)")
    public void starCount(String project, int stars) {
        assertThat(api.get("/api/v2/projects/" + project, null).body().path("starCount").asInt()).isEqualTo(stars);
    }

    @Then("{string} sees that they starred {string}")
    public void starredByViewer(String user, String project) {
        assertThat(api.get("/api/v2/projects/" + project, world.tokenOf(user)).body().path("starredByViewer").asBoolean())
                .isTrue();
    }

    @When("{string} views {string} {int} time(s)")
    public void view(String user, String project, int times) {
        for (int i = 0; i < times; i++) {
            assertThat(api.post("/api/v2/projects/" + project + "/views", world.tokenOf(user), null).status()).isEqualTo(204);
        }
    }

    @Then("{string} has been viewed {int} time(s)")
    public void viewCount(String project, int views) {
        assertThat(api.get("/api/v2/projects/" + project, null).body().path("watchCount").asInt()).isEqualTo(views);
    }

    @When("an anonymous visitor asks for the {int} most popular projects")
    public void popular(int size) {
        world.lastResponse(api.get("/api/v2/projects?sort=POPULAR&size=" + size, null));
    }

    @Then("the first popular project is {string}")
    public void firstPopular(String project) {
        JsonNode content = world.lastResponse().body().path("content");
        assertThat(content.size()).isEqualTo(3);
        assertThat(content.path(0).path("name").asString()).isEqualTo(project);
    }

    static JsonNode detail(ApiClient api, String project, String token) {
        ApiResponse response = api.get("/api/v2/projects/" + project, token);
        assertThat(response.status()).as(response.body().toString()).isEqualTo(200);
        return response.body();
    }

    static long versionId(ApiClient api, String project, int number, String token) {
        return stream(detail(api, project, token).path("versions"))
                .filter(version -> version.path("number").asInt() == number)
                .findFirst()
                .orElseThrow()
                .path("id").asLong();
    }

    static java.util.stream.Stream<JsonNode> stream(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false);
    }

    private static List<String> split(String commaSeparated) {
        return Arrays.stream(commaSeparated.split(",")).map(String::strip).toList();
    }
}
