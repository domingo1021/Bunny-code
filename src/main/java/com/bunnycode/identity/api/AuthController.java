package com.bunnycode.identity.api;

import com.bunnycode.identity.application.AuthResult;
import com.bunnycode.identity.application.AuthService;
import com.bunnycode.identity.domain.EmailAddress;
import com.bunnycode.identity.domain.PasswordPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/auth")
class AuthController {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/sign-up")
    @ResponseStatus(HttpStatus.CREATED)
    AuthResult signUp(@Valid @RequestBody SignUpRequest request) {
        return authService.signUp(request.name(), request.email(), request.password());
    }

    @PostMapping("/sign-in")
    AuthResult signIn(@Valid @RequestBody SignInRequest request) {
        return authService.signIn(request.email(), request.password());
    }

    record SignUpRequest(
            @NotBlank(message = "Please enter a user name.") @Size(max = 30, message = "User names can be up to 30 characters.")
            String name,
            @NotBlank(message = "Please enter your e-mail address.")
            @Pattern(regexp = EmailAddress.REGEX, message = "Please enter a valid e-mail address.")
            String email,
            @NotBlank(message = PasswordPolicy.MESSAGE) @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
            String password) {

        @Override
        public String toString() {
            return "SignUpRequest[name=" + name + ", email=" + email + ", password=<redacted>]";
        }
    }

    /** No password policy on sign-in: older accounts may predate it, and rule hints would leak information. */
    record SignInRequest(
            @NotBlank(message = "Please enter your e-mail address.") String email,
            @NotBlank(message = "Please enter your password.") String password) {

        @Override
        public String toString() {
            return "SignInRequest[email=" + email + ", password=<redacted>]";
        }
    }
}
