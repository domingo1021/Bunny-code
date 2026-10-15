package com.bunnycode.bdd;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Plain HTTP against the running app. Never throws on 4xx/5xx — scenarios assert on them. The
 * client is built on first use because the random port is only known once the server started.
 */
public class ApiClient {

    private final Environment environment;
    private final JsonMapper json;
    private RestClient http;

    ApiClient(Environment environment, JsonMapper json) {
        this.environment = environment;
        this.json = json;
    }

    private synchronized RestClient http() {
        if (http == null) {
            http = RestClient.builder()
                    .baseUrl("http://localhost:" + environment.getRequiredProperty("local.server.port"))
                    .defaultStatusHandler(status -> true, (request, response) -> { })
                    .build();
        }
        return http;
    }

    public ApiResponse get(String path, String token) {
        return call(HttpMethod.GET, path, token, null);
    }

    public ApiResponse post(String path, String token, Object body) {
        return call(HttpMethod.POST, path, token, body);
    }

    public ApiResponse put(String path, String token) {
        return call(HttpMethod.PUT, path, token, null);
    }

    public ApiResponse delete(String path, String token) {
        return call(HttpMethod.DELETE, path, token, null);
    }

    private ApiResponse call(HttpMethod method, String path, String token, Object body) {
        RestClient.RequestBodySpec request = http().method(method).uri(path);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).body(json.writeValueAsString(body));
        }
        ResponseEntity<String> response = request.retrieve().toEntity(String.class);
        String raw = response.getBody();
        JsonNode parsed = raw == null || raw.isBlank() ? json.nullNode() : json.readTree(raw);
        return new ApiResponse(response.getStatusCode().value(), parsed, response.getHeaders());
    }

    public record ApiResponse(int status, JsonNode body, HttpHeaders headers) {
    }
}
