package com.bunnycode.identity.api;

import com.bunnycode.identity.application.UserProfileView;
import com.bunnycode.identity.application.UserQueryService;
import com.bunnycode.shared.security.AuthenticatedUser;
import com.bunnycode.shared.security.CurrentUser;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/users")
class UserController {

    private final UserQueryService userQueryService;

    UserController(UserQueryService userQueryService) {
        this.userQueryService = userQueryService;
    }

    @GetMapping("/me")
    UserProfileView me(@CurrentUser AuthenticatedUser user) {
        return userQueryService.ownProfileOf(user.id());
    }

    @GetMapping("/{userId}")
    UserProfileView profile(@PathVariable @Positive long userId) {
        return userQueryService.profileOf(userId);
    }
}
