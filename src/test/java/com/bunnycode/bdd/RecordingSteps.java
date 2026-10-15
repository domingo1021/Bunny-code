package com.bunnycode.bdd;

import static org.assertj.core.api.Assertions.assertThat;

import com.bunnycode.project.application.RecordingProperties;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.domain.WritePrecision;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

public class RecordingSteps {

    private final World world;
    private final ApiClient api;
    private final JdbcTemplate jdbc;
    private final InfluxDBClient influx;
    private final RecordingProperties recordingProperties;

    public RecordingSteps(World world, ApiClient api, JdbcTemplate jdbc, InfluxDBClient influx,
                          RecordingProperties recordingProperties) {
        this.world = world;
        this.api = api;
        this.jdbc = jdbc;
        this.influx = influx;
        this.recordingProperties = recordingProperties;
    }

    @When("{string} saves this session on version {int} of {string}:")
    public void saveSession(String user, int version, String project, DataTable table) {
        saveSession(user, version, project, table.asMaps());
    }

    private void saveSession(String user, int version, String project, List<Map<String, String>> rows) {
        List<Map<String, Object>> events = new ArrayList<>();
        var finalCode = new StringBuilder();
        long timestamp = Instant.now().toEpochMilli();
        for (Map<String, String> row : rows) {
            String code = CommonSteps.unescape(row.get("code"));
            finalCode.append(code);
            events.add(Map.of("action", row.get("action"), "line", Integer.parseInt(row.get("line")),
                    "index", Integer.parseInt(row.get("index")), "code", code, "timestamp", timestamp++));
        }
        world.lastResponse(api.post("/api/v2/projects/%s/versions/%d/recording".formatted(project, version),
                world.tokenOf(user), Map.of("events", events, "code", finalCode.toString())));
    }

    @Given("{string} has saved a session on version {int} of {string}")
    public void hasSavedSession(String user, int version, String project) {
        saveSession(user, version, project, List.of(Map.of("action", "create", "line", "0", "index", "0", "code", "x")));
        assertThat(world.lastResponse().status()).as(world.lastResponse().body().toString()).isEqualTo(201);
    }

    @Then("the current file of version {int} of {string} contains exactly {string}")
    public void currentFileContent(int version, String project, String expected) {
        JsonNode versionNode = ProjectSteps.stream(ProjectSteps.detail(api, project, null).path("versions"))
                .filter(node -> node.path("number").asInt() == version).findFirst().orElseThrow();
        String url = versionNode.path("file").path("url").asString();
        String content = RestClient.create().get().uri(url).retrieve().body(String.class);
        assertThat(content).isEqualTo(CommonSteps.unescape(expected));
    }

    @Then("version {int} of {string} has a recording")
    public void hasRecording(int version, String project) {
        JsonNode versionNode = ProjectSteps.stream(ProjectSteps.detail(api, project, null).path("versions"))
                .filter(node -> node.path("number").asInt() == version).findFirst().orElseThrow();
        assertThat(versionNode.path("recording").isObject()).isTrue();
        assertThat(versionNode.path("recording").path("baseFileUrl").asString()).endsWith("/record/user_0/project_0/version_0/test.js");
    }

    @When("an anonymous visitor replays version {int} of {string}")
    public void anonymousReplay(int version, String project) {
        world.lastResponse(api.get("/api/v2/projects/%s/versions/%d/recording".formatted(project, version), null));
    }

    @When("{string} replays version {int} of {string}")
    public void userReplay(String user, int version, String project) {
        world.lastResponse(api.get("/api/v2/projects/%s/versions/%d/recording".formatted(project, version),
                world.tokenOf(user)));
    }

    @Then("the replay format is {string}")
    public void replayFormat(String format) {
        assertThat(world.lastResponse().body().path("format").asString()).isEqualTo(format);
    }

    @Then("the replay events are, in order:")
    public void replayEvents(DataTable table) {
        List<List<String>> actual = ProjectSteps.stream(world.lastResponse().body().path("events"))
                .map(event -> List.of(event.path("action").asString(), String.valueOf(event.path("line").asInt()),
                        String.valueOf(event.path("index").asInt()), event.path("code").asString()))
                .toList();
        List<List<String>> expected = table.asMaps().stream()
                .map(row -> List.of(row.get("action"), row.get("line"), row.get("index"), CommonSteps.unescape(row.get("code"))))
                .toList();
        assertThat(actual).isEqualTo(expected);
    }

    @Given("version {int} of {string} has a v1 recording with a {string} of {string} at line {int} index {int}")
    public void legacyRecording(int version, String project, String action, String code, int line, int index) {
        Instant at = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        long projectId = jdbc.queryForObject("select project_id from project where project_name = ?", Long.class, project);
        long versionId = ProjectSteps.versionId(api, project, version, null);
        // v1 padded the session window by 1,000,000 ms on both sides.
        jdbc.update("insert into record (start_time, end_time, version_id, base_url) values (?, ?, ?, ?)",
                Timestamp.from(at.minusMillis(1_000_000)), Timestamp.from(at.plusMillis(1_000_000)), versionId,
                "/record/user_0/project_0/version_0/test.js");
        String lineProtocol = "%d,version=%d,file=1,action=%s,line=%d,index=%d code=\"%s\" %d"
                .formatted(projectId, versionId, action, line, index, code, at.toEpochMilli() * 1_000_000L);
        influx.getWriteApiBlocking().writeRecord(recordingProperties.influx().bucket(), recordingProperties.influx().org(),
                WritePrecision.NS, lineProtocol);
    }
}
