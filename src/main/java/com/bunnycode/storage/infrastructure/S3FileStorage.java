package com.bunnycode.storage.infrastructure;

import com.bunnycode.storage.FileStorage;
import java.util.Objects;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
class S3FileStorage implements FileStorage {

    private final S3Client s3;
    private final StorageProperties properties;

    S3FileStorage(S3Client s3, StorageProperties properties) {
        this.s3 = s3;
        this.properties = properties;
    }

    @Override
    public void store(String key, byte[] content, String contentType) {
        Objects.requireNonNull(content, "content");
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey(key))
                .contentType(contentType)
                .build();
        s3.putObject(request, RequestBody.fromBytes(content));
    }

    @Override
    public String publicUrl(String key) {
        return properties.publicBaseUrl() + "/" + objectKey(key);
    }

    /** Database keys start with '/'; S3 object keys must not. */
    static String objectKey(String key) {
        Objects.requireNonNull(key, "key");
        String trimmed = key.startsWith("/") ? key.substring(1) : key;
        if (trimmed.isBlank() || trimmed.contains("..")) {
            throw new IllegalArgumentException("Invalid storage key: " + key);
        }
        return trimmed;
    }
}
