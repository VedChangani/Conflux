package com.conflux.user;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Bean Validation constraint for usernames, delegating to {@link User#isValidUsername(String)}
 * so request validation and the entity always apply the same rule. {@code null} is
 * considered valid; combine with {@code @NotBlank} where the value is required.
 */
@Documented
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidUsername.Validator.class)
public @interface ValidUsername {

	String message() default "must be " + User.USERNAME_RULE;

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<ValidUsername, String> {

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			return value == null || User.isValidUsername(value);
		}

	}

}
