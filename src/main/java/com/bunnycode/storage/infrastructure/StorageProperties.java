package com.bunnycode.storage.infrastructure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param endpoint  S3-compatible endpoint override (LocalStack, MinIO); blank means real AWS
 * @param accessKey static credentials for a non-AWS endpoint; blank means the default AWS chain
 */
@Validated
@ConfigurationProperties("bunny.storage")
public record StorageProperties(
        @NotBlank String bucket,
        @NotBlank String region,
        @NotBlank @Pattern(regexp = "^https?://.+[^/]$", message = "must be an http(s) URL without a trailing slash")
        String publicBaseUrl,
        String endpoint,
        String accessKey,
        String secretKey,
        @NotBlank String recordFolder,
        @NotBlank String battleFolder,
        @NotBlank @Pattern(regexp = "^/.+", message = "must start with '/'") String defaultTemplateKey) {

    Optional<URI> endpointOverride() {
        return endpoint == null || endpoint.isBlank() ? Optional.empty() : Optional.of(URI.create(endpoint));
    }

    boolean hasStaticCredentials() {
        return accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank();
    }

    @Override
    public String toString() {
        return "StorageProperties[bucket=" + bucket + ", region=" + region + ", publicBaseUrl=" + publicBaseUrl
                + ", endpoint=" + endpoint + ", credentials=" + (hasStaticCredentials() ? "static" : "default") + "]";
    }
}
