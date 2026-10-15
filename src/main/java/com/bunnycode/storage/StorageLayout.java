package com.bunnycode.storage;

import com.bunnycode.storage.infrastructure.StorageProperties;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** The one place that knows how storage keys are laid out. */
@Component
public class StorageLayout {

    private final StorageProperties properties;

    StorageLayout(StorageProperties properties) {
        this.properties = Objects.requireNonNull(properties);
    }

    /** Starter file every new project begins with. */
    public String defaultTemplateKey() {
        return properties.defaultTemplateKey();
    }

    public String codeFileKey(long userId, long projectId, long versionId, String revision, String fileName) {
        return "/%s/user_%d/project_%d/version_%d/%s-%s"
                .formatted(properties.recordFolder(), userId, projectId, versionId, revision, fileName);
    }

    public String battleWinnerKey(long battleId, long winnerId) {
        return "/%s/battle_%d/%d.js".formatted(properties.battleFolder(), battleId, winnerId);
    }
}
