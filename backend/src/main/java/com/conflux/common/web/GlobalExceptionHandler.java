package com.conflux.common.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Global REST error handling based on RFC 9457 {@link ProblemDetail}.
 * <p>
 * Standard Spring MVC exceptions (validation failures, malformed request bodies,
 * unsupported methods, missing resources, {@code ResponseStatusException}, etc.) are
 * handled by {@link ResponseEntityExceptionHandler}. Anything else becomes a generic
 * 500 response that does not reveal internal details.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception ex) {
		log.error("Unexpected error while processing request", ex);
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
	}

}
