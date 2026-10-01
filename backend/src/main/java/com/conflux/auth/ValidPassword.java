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

		static int utf8Length(String value) {
			return value.getBytes(StandardCharsets.UTF_8).length;
		}

	}

}
