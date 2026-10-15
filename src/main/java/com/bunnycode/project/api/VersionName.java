package com.bunnycode.project.api;

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
 * A version's display name, e.g. "version 2". Unlike project and file names it never becomes part
 * of a URL or storage key (versions are addressed by number), so spaces are fine: 1–30 printable
 * characters without leading or trailing whitespace.
 */
@Documented
@Constraint(validatedBy = {})
@NotNull
@Pattern(regexp = "^[^\\s\\p{Cntrl}](?:[^\\p{Cntrl}]{0,28}[^\\s\\p{Cntrl}])?$")
@ReportAsSingleViolation
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface VersionName {

    String message() default "Version names need 1–30 characters and cannot start or end with a space.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
