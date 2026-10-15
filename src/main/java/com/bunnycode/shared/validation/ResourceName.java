package com.bunnycode.shared.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * A name chosen by a user for a project, version, file or battle: 1–20 letters, digits,
 * {@code _}, {@code -} or {@code .}. Kept identical to the v1 rule so existing names stay valid.
 */
@Documented
@Constraint(validatedBy = {})
@NotNull
@Pattern(regexp = ResourceName.REGEX)
@ReportAsSingleViolation
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface ResourceName {

    String REGEX = "^[a-zA-Z0-9_\\-.]{1,20}$";

    String message() default "Names may only use letters, digits, '_', '-' or '.', up to 20 characters.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
