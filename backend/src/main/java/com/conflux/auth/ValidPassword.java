package com.conflux.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Password length measured in UTF-8 bytes, which is what BCrypt actually hashes: BCrypt
 * uses at most {@value #MAX_BYTES} bytes and Spring Security refuses to hash more. The
 * value is treated as an opaque secret: it is never trimmed or altered, and it is not
 * part of the violation message. {@code null} is considered valid; combine with
 * {@code @NotNull} where the value is required.
 */
@Documented
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidPassword.Validator.class)
public @interface ValidPassword {

	int MIN_BYTES = 8;

	int MAX_BYTES = 72;

	String message() default "must be between " + MIN_BYTES + " and " + MAX_BYTES + " bytes (UTF-8)";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<ValidPassword, String> {

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			if (value == null) {
				return true;
			}
			int bytes = utf8Length(value);
			return bytes >= MIN_BYTES && bytes <= MAX_BYTES;
		}

		/**
		 * UTF-8 length using the same conversion BCrypt applies before hashing.
		 */
		static int utf8Length(String value) {
			return value.getBytes(StandardCharsets.UTF_8).length;
		}

	}

}
