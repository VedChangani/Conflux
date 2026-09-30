package com.conflux.listing;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Editable listing content, used both to create a listing ({@code POST /listings}) and to
 * replace its content ({@code PUT /listings/{id}}): the two operations accept exactly the
 * same fields.
 * <p>
 * Owner, slug, status and timestamps are deliberately absent: the server controls them.
 * JSON properties that are not listed here (e.g. {@code ownerId}, {@code status},
 * {@code slug}) are not bound to anything and have no effect.
 * <p>
 * The text limits keep content well inside MySQL {@code TEXT} (65,535 bytes).
 */
@ListingRequest.ConsistentPrice
public record ListingRequest(
		@NotBlank @Size(max = 120) String title,
		@NotBlank @Size(max = 240) String shortPitch,
		@NotBlank @Size(max = 10_000) String description,
		@Size(max = 10_000) String problem,
		@Size(max = 10_000) String solution,
		@NotNull ListingAssetType assetType,
		@NotNull ListingMarketplaceMode marketplaceMode,
		@NotNull ListingCategory category,
		@NotNull ListingStage stage,
		@PositiveOrZero @Digits(integer = 13, fraction = 2) BigDecimal askingPrice,
		@Pattern(regexp = "[A-Z]{3}", message = "must be three upper-case letters, e.g. USD") String currency,
		Boolean priceNegotiable,
		@Size(max = 5_000) String collaborationDetails) {

	/**
	 * {@code priceNegotiable} defaults to {@code false} when omitted.
	 */
	boolean priceNegotiableOrDefault() {
		return Boolean.TRUE.equals(this.priceNegotiable);
	}

	/**
	 * {@code askingPrice} and {@code currency} must be given together or not at all.
	 * Violations are reported on the {@code currency} field.
	 */
	@Documented
	@Target(ElementType.TYPE)
	@Retention(RetentionPolicy.RUNTIME)
	@Constraint(validatedBy = ConsistentPrice.Validator.class)
	public @interface ConsistentPrice {

		String message() default "must be given together with askingPrice";

		Class<?>[] groups() default {};

		Class<? extends Payload>[] payload() default {};

		class Validator implements ConstraintValidator<ConsistentPrice, ListingRequest> {

			@Override
			public boolean isValid(ListingRequest request, ConstraintValidatorContext context) {
				if (request == null) {
					return true;
				}
				String problem = null;
				if (request.askingPrice() != null && request.currency() == null) {
					problem = "is required when askingPrice is set";
				}
				else if (request.askingPrice() == null && request.currency() != null) {
					problem = "must not be set without askingPrice";
				}
				if (problem == null) {
					return true;
				}
				context.disableDefaultConstraintViolation();
				context.buildConstraintViolationWithTemplate(problem).addPropertyNode("currency").addConstraintViolation();
				return false;
			}

		}

	}

}
