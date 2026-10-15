package com.bunnycode.project.api;

import com.bunnycode.project.application.RecordingService;
import com.bunnycode.project.application.RecordingView;
import com.bunnycode.project.application.SavedRecordingView;
import com.bunnycode.project.domain.CodingEvent;
import com.bunnycode.shared.security.AuthenticatedUser;
import com.bunnycode.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A version's recorded coding session. Saving replaces v1's two calls ({@code POST /record}
 * then {@code POST /record/file}) with one, and the base file is taken from the server's own
 * state instead of a client-supplied URL.
 */
@RestController
@RequestMapping("/api/v2/projects/{projectName}/versions/{versionNumber}/recording")
class RecordingController {

    private final RecordingService recordings;

    RecordingController(RecordingService recordings) {
        this.recordings = recordings;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SavedRecordingView save(@PathVariable String projectName, @PathVariable @Positive int versionNumber,
                            @CurrentUser AuthenticatedUser user, @Valid @RequestBody SaveRecordingRequest request) {
        List<CodingEvent> events = request.events().stream().map(EventPayload::toDomain).toList();
        return recordings.save(user, projectName, versionNumber, events, request.code());
    }

    @GetMapping
    RecordingView replay(@PathVariable String projectName, @PathVariable @Positive int versionNumber,
                         @CurrentUser Optional<AuthenticatedUser> viewer) {
        return recordings.recordingOf(viewer, projectName, versionNumber);
    }

    /** @param code the final source after the session, stored as the version's new file revision */
    record SaveRecordingRequest(
            @NotEmpty(message = "There is nothing to save yet — write some code first.") List<@Valid @NotNull EventPayload> events,
            @NotNull @Size(max = 200_000, message = "Files can be up to 200,000 characters.") String code) {

        SaveRecordingRequest {
            events = events == null ? null : List.copyOf(events);
        }
    }

    /** @param timestamp epoch milliseconds */
    record EventPayload(
            @NotBlank @Pattern(regexp = CodingEvent.ACTION_REGEX) String action,
            @PositiveOrZero int line,
            @PositiveOrZero int index,
            @NotNull @Size(max = CodingEvent.MAX_CODE_LENGTH) String code,
            @Positive long timestamp) {

        CodingEvent toDomain() {
            return new CodingEvent(action, line, index, code, Instant.ofEpochMilli(timestamp));
        }
    }
}
