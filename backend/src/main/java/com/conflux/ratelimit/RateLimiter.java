package com.conflux.ratelimit;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import com.conflux.ratelimit.RateLimitProperties.Limit;

public class RateLimiter {

	static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(1);

	private final RateLimitProperties properties;

	private final LongSupplier nanoTime;

	private final ConcurrentHashMap<Key, Window> windows = new ConcurrentHashMap<>();

	private final AtomicLong nextCleanup;

	public RateLimiter(RateLimitProperties properties, LongSupplier nanoTime) {
		this.properties = properties;
		this.nanoTime = nanoTime;
		this.nextCleanup = new AtomicLong(nanoTime.getAsLong() + CLEANUP_INTERVAL.toNanos());
	}

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

	private void cleanUpIfDue(long now) {
		long due = this.nextCleanup.get();
		if (now - due >= 0 && this.nextCleanup.compareAndSet(due, now + CLEANUP_INTERVAL.toNanos())) {
			removeExpired(now);
		}
	}

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
