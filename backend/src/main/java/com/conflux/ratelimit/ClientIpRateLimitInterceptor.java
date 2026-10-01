package com.conflux.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Limits anonymous POST requests by client IP before the handler runs, so requests that
 * would fail validation or authentication are counted too. The IP is the connection's
 * remote address; proxy headers such as {@code X-Forwarded-For} are client-controlled and
 * never used.
 */
class ClientIpRateLimitInterceptor implements HandlerInterceptor {

	private final RateLimiter rateLimiter;

	private final RateLimitOperation operation;

	ClientIpRateLimitInterceptor(RateLimiter rateLimiter, RateLimitOperation operation) {
		this.rateLimiter = rateLimiter;
		this.operation = operation;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (HttpMethod.POST.matches(request.getMethod())) {
			this.rateLimiter.acquire(this.operation, request.getRemoteAddr());
		}
		return true;
	}

}
