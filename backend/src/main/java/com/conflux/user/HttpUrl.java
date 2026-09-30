package com.conflux.user;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * A syntactically valid absolute {@code http} or {@code https} URL with a host, e.g.
 * {@code https://example.com/me}. Purely syntactic: the URL is never fetched and ownership
 * is never checked. {@code null} is considered valid.
 */
@Documented
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = HttpUrl.Validator.class)
public @interface HttpUrl {

	String message() default "must be a valid http:// or https:// URL";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	class Validator implements ConstraintValidator<HttpUrl, String> {

		@Override
		public boolean isValid(String value, ConstraintValidatorContext context) {
			return value == null || isHttpUrl(value);
		}

		static boolean isHttpUrl(String value) {
			try {
				URI uri = new URI(value);
				String scheme = uri.getScheme();
				return scheme != null
						&& ("http".equals(scheme.toLowerCase(Locale.ROOT))
								|| "https".equals(scheme.toLowerCase(Locale.ROOT)))
						&& uri.getHost() != null && !uri.getHost().isEmpty() && uri.getRawUserInfo() == null;
			}
			catch (URISyntaxException ex) {
				return false;
			}
		}

	}

}
