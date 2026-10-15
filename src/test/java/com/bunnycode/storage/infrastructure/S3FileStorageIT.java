package com.bunnycode.storage.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bunnycode.support.TestContainers;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

@DisplayName("S3 file storage (LocalStack)")
class S3FileStorageIT {

    private static S3FileStorage storage;
    private static StorageProperties properties;

    @BeforeAll
    static void setUp() {
        var localStack = TestContainers.localStack();
        properties = new StorageProperties(TestContainers.S3_BUCKET, localStack.getRegion(), TestContainers.publicBaseUrl(),
                localStack.getEndpoint().toString(), localStack.getAccessKey(), localStack.getSecretKey(),
                "record", "battle", "/record/template.js");
        storage = new S3FileStorage(new S3Configuration().s3Client(properties), properties);
    }

    @Test
    @DisplayName("stores raw source under the key without its leading slash (v1 stored JSON-quoted strings)")
    void storesRawSource() {
        storage.store("/record/user_1/project_2/version_3/1-main.js", "console.log(\"hi\")\n".getBytes(StandardCharsets.UTF_8),
                "application/javascript");

        try (S3Client s3 = TestContainers.s3Client()) {
            var object = s3.getObjectAsBytes(request -> request.bucket(TestContainers.S3_BUCKET)
                    .key("record/user_1/project_2/version_3/1-main.js"));
            assertThat(object.asUtf8String()).isEqualTo("console.log(\"hi\")\n");
            assertThat(object.response().contentType()).isEqualTo("application/javascript");
        }
    }

    @Test
    @DisplayName("public URLs join the CDN base and the key with exactly one slash")
    void publicUrl() {
        assertThat(storage.publicUrl("/user/user_icon.png")).isEqualTo(properties.publicBaseUrl() + "/user/user_icon.png");
        assertThat(storage.publicUrl("user/user_icon.png")).isEqualTo(properties.publicBaseUrl() + "/user/user_icon.png");
    }

    @Test
    @DisplayName("keys that try to climb out of their folder are refused")
    void rejectsTraversal() {
        assertThatThrownBy(() -> storage.store("/record/../secrets", new byte[0], "text/plain"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
