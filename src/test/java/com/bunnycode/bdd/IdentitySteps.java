package com.bunnycode.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import com.bunnycode.bdd.ApiClient.ApiResponse;
import com.bunnycode.bdd.World.Account;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class IdentitySteps {

    static final String DEFAULT_PASSWORD = "secret1";

    private final World world;
    private final ApiClient api;
    private final JdbcTemplate jdbc;

    public IdentitySteps(World world, ApiClient api, JdbcTemplate jdbc) {
        this.world = world;
        this.api = api;
        this.jdbc = jdbc;
    }

    @Given("{string} has an account")
    public void hasAccount(String name) {
        signUp(name, DEFAULT_PASSWORD);
        assertThat(world.lastResponse().status()).isEqualTo(201);
    }

    @When("{string} signs up with password {string}")
    public void signUp(String name, String password) {
        String email = name + "@example.com";
        ApiResponse response = api.post("/api/v2/auth/sign-up", null,
                Map.of("name", name, "email", email, "password", password));
        world.lastResponse(response);
        if (response.status() == 201) {
            world.remember(new Account(response.body().path("user").path("id").asLong(), name, email, password,
                    response.body().path("accessToken").asString()));
        }
    }

    @When("someone else signs up with the e-mail address of {string}")
    public void signUpWithTakenEmail(String name) {
        world.lastResponse(api.post("/api/v2/auth/sign-up", null,
                Map.of("name", "someone_else", "email", world.account(name).email(), "password", DEFAULT_PASSWORD)));
    }

    @When("{string} signs in with password {string}")
    public void signIn(String name, String password) {
        world.lastResponse(api.post("/api/v2/auth/sign-in", null,
                Map.of("email", world.account(name).email(), "password", password)));
    }

    @Given("a v1 user {string} whose password {string} was hashed with bcrypt cost {int}")
    public void legacyUser(String name, String password, int cost) {
        String legacyHash = new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2B, cost).encode(password);
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("insert into `user` (user_name, email, password) values (?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, name);
            statement.setString(2, name + "@example.com");
            statement.setString(3, legacyHash);
            return statement;
        }, keys);
        world.remember(new Account(keys.getKey().longValue(), name, name + "@example.com", password, null));
    }

    @Then("the stored password hash of {string} uses bcrypt cost {int}")
    public void storedHashCost(String name, int cost) {
        String hash = jdbc.queryForObject("select password from `user` where user_id = ?", String.class,
                world.account(name).id());
        assertThat(hash).startsWith("{bcrypt}$2a$%02d$".formatted(cost));
    }

    @Then("the response contains an access token")
    public void containsToken() {
        assertThat(world.lastResponse().body().path("accessToken").asString()).isNotBlank();
        assertThat(world.lastResponse().body().path("tokenType").asString()).isEqualTo("Bearer");
        assertThat(world.lastResponse().body().path("expiresAt").asString()).isNotBlank();
    }

    @Then("{string} can see their own profile including their e-mail")
    public void ownProfile(String name) {
        ApiResponse me = api.get("/api/v2/users/me", world.tokenOf(name));
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.body().path("email").asString()).isEqualTo(world.account(name).email());
    }

    @When("an anonymous visitor views the public profile of {string}")
    public void publicProfile(String name) {
        world.lastResponse(api.get("/api/v2/users/" + world.account(name).id(), null));
    }

    @Then("the response has no e-mail address")
    public void noEmail() {
        assertThat(world.lastResponse().body().has("email")).isFalse();
    }
}
