package com.bunnycode.shared.security;

import com.bunnycode.shared.error.BunnyException;
import java.util.Optional;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

final class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        if (!parameter.hasParameterAnnotation(CurrentUser.class)) {
            return false;
        }
        Class<?> type = parameter.getParameterType();
        return type == AuthenticatedUser.class
                || type == Optional.class && parameter.nested().getNestedParameterType() == AuthenticatedUser.class;
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Optional<AuthenticatedUser> user = currentUser();
        if (parameter.getParameterType() == Optional.class) {
            return user;
        }
        return user.orElseThrow(() -> BunnyException.unauthenticated("auth.required", "Please sign in to continue."));
    }

    private static Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return Optional.of(AuthenticatedUser.from(token.getToken()));
        }
        return Optional.empty();
    }
}
