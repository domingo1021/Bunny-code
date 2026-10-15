package com.bunnycode.execution.api;

import com.bunnycode.execution.application.ExecutionOutcome;
import com.bunnycode.execution.application.ExecutionService;
import com.bunnycode.shared.security.AuthenticatedUser;
import com.bunnycode.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ExecutionController {

    private final ExecutionService executions;

    ExecutionController(ExecutionService executions) {
        this.executions = executions;
    }

    @PostMapping("/api/v2/executions")
    ExecutionOutcome run(@CurrentUser AuthenticatedUser user, @Valid @RequestBody RunCodeRequest request) {
        return executions.run(user, request.code());
    }

    record RunCodeRequest(@NotNull @Size(max = 200_000, message = "Programs can be up to 200,000 characters.") String code) {
    }
}
