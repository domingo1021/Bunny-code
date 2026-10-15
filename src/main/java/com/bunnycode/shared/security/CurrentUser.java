package com.bunnycode.shared.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the caller into a controller method. Declare the parameter as {@link AuthenticatedUser}
 * when the endpoint requires sign-in, or as {@code Optional<AuthenticatedUser>} when anonymous
 * callers are allowed and the response merely differs for signed-in users.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
