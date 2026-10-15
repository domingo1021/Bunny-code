package com.bunnycode.shared.security;

import static org.springframework.security.config.Customizer.withDefaults;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Stateless JWT resource server. Authorization is declared here per route; finer, data-dependent
 * rules (ownership, project visibility) live in the application services that own the data.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfiguration implements WebMvcConfigurer {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, SecurityProperties properties) throws Exception {
        var problemHandlers = new ProblemDetailSecurityHandlers();
        http
                .cors(withDefaults())
                // The resource server exempts every request with a resolvable bearer token from the
                // default CSRF filter — which would include our cookie. A dedicated filter instead
                // protects exactly the cookie-authenticated writes (double-submit cookie).
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(cookieSessionCsrfFilter(properties, problemHandlers), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new CsrfCookieFilter(properties.sessionCookieName()), BearerTokenAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v2/auth/sign-up", "/api/v2/auth/sign-in").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v2/users/me").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v2/users/*", "/api/v2/users/*/projects",
                                "/api/v2/projects", "/api/v2/projects/**",
                                "/api/v2/battles", "/api/v2/battles/**", "/api/v2/stats/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v2/projects/*/views").permitAll()
                        // The handshake is anonymous; the STOMP CONNECT frame is authenticated instead.
                        .requestMatchers("/api/v2/ws", "/api/v2/ws/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .bearerTokenResolver(new HeaderOrCookieBearerTokenResolver(properties.sessionCookieName()))
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers)
                        .jwt(withDefaults()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers));
        return http.build();
    }

    private static CsrfFilter cookieSessionCsrfFilter(SecurityProperties properties, AccessDeniedHandler deniedHandler) {
        var filter = new CsrfFilter(CookieCsrfTokenRepository.withHttpOnlyFalse());
        filter.setRequireCsrfProtectionMatcher(new CookieAuthenticatedWriteMatcher(properties.sessionCookieName()));
        // The client echoes the raw cookie value, so no BREACH masking (it never appears in a page body).
        filter.setRequestHandler(new CsrfTokenRequestAttributeHandler());
        filter.setAccessDeniedHandler(deniedHandler);
        return filter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "X-XSRF-TOKEN"));
        cors.setExposedHeaders(List.of(HttpHeaders.LOCATION));
        cors.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }
}
