package com.conflux.ratelimit;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import com.conflux.ratelimit.RateLimitProperties.Limit;

/**
 * In-memory fixed-window rate limiter for a single application instance.
 * <p>
 * Each (operation, subject) pair has a counter that starts with the pair's first request
 * and allows the operation's {@code maxRequests} requests until its {@code window} has
 * passed; the next request after that starts a new window. The subject is the client IP
 * or the authenticated user id, as chosen by the caller.
 * <p>
 * Counters are updated atomically per key ({@link ConcurrentHashMap#compute}), so
 * concurrent requests can never be allowed more than the limit. Expired counters are
 * removed at most once per {@link #CLEANUP_INTERVAL}, and also whenever the table is full.
 * The table holds at most about {@code maxTrackedKeys} counters: while it is full of live
 * counters, requests from new keys are rejected. Time comes from a monotonic clock, so
 * wall-clock changes do not affect the windows.
 */
public class RateLimiter {

	static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(1);

	private final RateLimitProperties properties;

	private final LongSupplier nanoTime;

	private final ConcurrentHashMap<Key, Window> windows = new ConcurrentHashMap<>();

	private final AtomicLong nextCleanup;

	/**
	 * @param nanoTime monotonic time source in nanoseconds (normally {@code System::nanoTime})
	 */
	public RateLimiter(RateLimitProperties properties, LongSupplier nanoTime) {
		this.properties = properties;
		this.nanoTime = nanoTime;
		this.nextCleanup = new AtomicLong(nanoTime.getAsLong() + CLEANUP_INTERVAL.toNanos());
	}

	/**
	 * Counts one request of {@code subject} for {@code operation}.
	 * @param subject the verified identity the limit applies to (client IP or user id)
	 * @throws RateLimitExceededException if the allowance of the current window is used up
	 */
	public void acquire(RateLimitOperation operation, String subject) {
		long now = this.nanoTime.getAsLong();
		cleanUpIfDue(now);
		Limit limit = this.properties.limitFor(operation);
		Key key = new Key(operation, subject);
		if (isFull() && !this.windows.containsKey(key)) {
			removeExpired(now);
			if (isFull()) {
				throw new RateLimitExceededException(limit.window().toSeconds());
			}
		}
		long windowNanos = limit.window().toNanos();
		// The count stops at maxRequests + 1: rejected requests neither overflow it nor extend the window.
		Window window = this.windows.compute(key,
				(k, current) -> (current == null || current.hasEnded(now)) ? new Window(now + windowNanos, 1)
						: new Window(current.endsAt(), Math.min(current.count() + 1, limit.maxRequests() + 1)));
		if (window.count() > limit.maxRequests()) {
			throw new RateLimitExceededException(retryAfterSeconds(window.endsAt() - now));
		}
	}

	int trackedKeys() {
		return this.windows.size();
	}

	private boolean isFull() {
		return this.windows.size() >= this.properties.maxTrackedKeys();
	}

	// Only the thread that advances nextCleanup sweeps, so concurrent requests do not all sweep at once.
	private void cleanUpIfDue(long now) {
		long due = this.nextCleanup.get();
		if (now - due >= 0 && this.nextCleanup.compareAndSet(due, now + CLEANUP_INTERVAL.toNanos())) {
			removeExpired(now);
		}
	}

	// Removes an entry only if it still is the expired window that was read; a concurrently renewed one stays.
	private void removeExpired(long now) {
		this.windows.values().removeIf(window -> window.hasEnded(now));
	}

	private static long retryAfterSeconds(long remainingNanos) {
		return Math.max(1, Math.ceilDiv(remainingNanos, TimeUnit.SECONDS.toNanos(1)));
	}

	private record Key(RateLimitOperation operation, String subject) {
	}

	private record Window(long endsAt, int count) {

		boolean hasEnded(long now) {
			return now - this.endsAt >= 0;
		}

	}

}
