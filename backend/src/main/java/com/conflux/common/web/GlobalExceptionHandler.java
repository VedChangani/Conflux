package com.conflux.common.web;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Global REST error handling based on RFC 9457 {@link ProblemDetail}.
 * <p>
 * Standard Spring MVC exceptions (validation failures, malformed request bodies,
 * unsupported methods, missing resources, {@code ResponseStatusException}, etc.) are
 * handled by {@link ResponseEntityExceptionHandler}. Spring Security failures are mapped
 * to 401/403; the security filter chain also routes its own failures here. Anything else
 * becomes a generic 500 response that does not reveal internal details.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/**
	 * 401. Exception messages are never exposed: login failures get a fixed message that
	 * does not reveal whether the account exists, token failures a generic one.
	 */
	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
		String detail;
		if (ex instanceof BadCredentialsException) {
			detail = "Invalid email/username or password.";
		}
		else if (ex instanceof LockedException) {
			detail = "This account is suspended.";
		}
		else {
			detail = "A valid access token is required.";
		}
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
			.body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail));
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
				"You do not have permission to access this resource.");
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unexpected error while processing request", ex);
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
	}

	/**
	 * Adds the failing fields to the standard 400 response. Rejected values are not
	 * echoed back, so submitted passwords never appear in responses.
	 */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<Map<String, String>> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> Map.of("field", error.getField(), "message",
					Objects.requireNonNullElse(error.getDefaultMessage(), "is invalid")))
			.toList();
		ProblemDetail body = ex.getBody();
		body.setProperty("errors", errors);
		return handleExceptionInternal(ex, body, headers, status, request);
	}

}
