package com.conflux.ratelimit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.conflux.ratelimit.RateLimitProperties.Limit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * The limiter itself, with a controllable clock.
 */
class RateLimiterTest {

	private static final Limit THREE_PER_MINUTE = new Limit(3, Duration.ofMinutes(1));

	private static final Limit HUNDRED_PER_HOUR = new Limit(100, Duration.ofHours(1));

	// Deliberately not 0: the limiter must only rely on differences between readings.
	private final AtomicLong nanos = new AtomicLong(Long.MAX_VALUE - Duration.ofMinutes(5).toNanos());

	private final RateLimiter limiter = new RateLimiter(properties(100), this.nanos::get);

	@Test
	void requestsUpToTheLimitPassAndTheNextIsRejectedWithRetryAfter() {
		for (int i = 0; i < 3; i++) {
			this.limiter.acquire(RateLimitOperation.LOGIN, "10.0.0.1");
		}
		advance(Duration.ofSeconds(20).plusMillis(500));

		assertThatExceptionOfType(RateLimitExceededException.class)
			.isThrownBy(() -> this.limiter.acquire(RateLimitOperation.LOGIN, "10.0.0.1"))
			// 39.5 seconds of the window are left, rounded up.
			.satisfies(ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(40));
	}

	@Test
	void rejectedRequestsDoNotExtendTheWindow() {
		for (int i = 0; i < 3; i++) {
			this.limiter.acquire(RateLimitOperation.LOGIN, "10.0.0.1");
		}
		for (int i = 0; i < 50; i++) {
			advance(Duration.ofSeconds(1));
			assertRejected(RateLimitOperation.LOGIN, "10.0.0.1");
		}
		advance(Duration.ofSeconds(10));

		this.limiter.acquire(RateLimitOperation.LOGIN, "10.0.0.1");
	}

	@Test
	void theWindowResetsOnceItHasPassed() {
		for (int i = 0; i < 3; i++) {
			this.limiter.acquire(RateLimitOperation.LOGIN, "10.0.0.1");
		}
		advance(Duration.ofSeconds(59).plusMillis(999));
		assertThatExceptionOfType(RateLimitExceededException.class)
			.isThrownBy(() -> this.limiter.acquire(RateLimitOperation.LOGIN, "10.0.0.1"))
			.satisfies(ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(1));

		advance(Duration.ofMillis(1));

		for (int i = 0; i < 3; i++) {
			this.limiter.acquire(RateLimitOperation.LOGIN, "10.0.0.1");
		}
		assertRejected(RateLimitOperation.LOGIN, "10.0.0.1");
	}

	@Test
	void subjectsAndOperationsHaveIndependentCounters() {
		for (int i = 0; i < 3; i++) {
			this.limiter.acquire(RateLimitOperation.LOGIN, "10.0.0.1");
		}
		assertRejected(RateLimitOperation.LOGIN, "10.0.0.1");

		assertThatCode(() -> {
			this.limiter.acquire(RateLimitOperation.LOGIN, "10.0.0.2");
			this.limiter.acquire(RateLimitOperation.REGISTER, "10.0.0.1");
			this.limiter.acquire(RateLimitOperation.MESSAGE_SEND, "10.0.0.1");
		}).doesNotThrowAnyException();
	}

	@Test
	void expiredEntriesAreRemoved() {
		for (int i = 0; i < 5; i++) {
			this.limiter.acquire(RateLimitOperation.LOGIN, "10.0.0." + i);
		}
		this.limiter.acquire(RateLimitOperation.LISTING_CREATE, "7");
		assertThat(this.limiter.trackedKeys()).isEqualTo(6);

		// The login windows (1 minute) have ended, the listing one (1 hour) has not.
		advance(RateLimiter.CLEANUP_INTERVAL.plusSeconds(1));
		this.limiter.acquire(RateLimitOperation.MESSAGE_SEND, "7");

		assertThat(this.limiter.trackedKeys()).isEqualTo(2);
		// The surviving counter kept its count.
		for (int i = 1; i < HUNDRED_PER_HOUR.maxRequests(); i++) {
			this.limiter.acquire(RateLimitOperation.LISTING_CREATE, "7");
		}
		assertRejected(RateLimitOperation.LISTING_CREATE, "7");
	}

	@Test
	void theNumberOfTrackedKeysIsBounded() {
		Limit threePer10Seconds = new Limit(3, Duration.ofSeconds(10));
		RateLimiter small = new RateLimiter(properties(2, threePer10Seconds), this.nanos::get);
		small.acquire(RateLimitOperation.LOGIN, "10.0.0.1");
		small.acquire(RateLimitOperation.LOGIN, "10.0.0.2");

		assertThatExceptionOfType(RateLimitExceededException.class)
			.isThrownBy(() -> small.acquire(RateLimitOperation.LOGIN, "10.0.0.3"))
			.satisfies(ex -> assertThat(ex.getRetryAfterSeconds()).isPositive());
		assertThat(small.trackedKeys()).isEqualTo(2);
		// Keys that are already tracked keep working.
		small.acquire(RateLimitOperation.LOGIN, "10.0.0.1");

		// Once windows have ended there is room again, even before the periodic cleanup is due.
		advance(Duration.ofSeconds(11));
		small.acquire(RateLimitOperation.LOGIN, "10.0.0.3");
		assertThat(small.trackedKeys()).isEqualTo(1);
	}

	@Test
	void concurrentRequestsCannotExceedTheLimit() throws Exception {
		int threads = 16;
		int attemptsPerThread = 50;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> results = new ArrayList<>();
		try {
			for (int t = 0; t < threads; t++) {
				Callable<Integer> call = () -> {
					start.await();
					int allowed = 0;
					for (int i = 0; i < attemptsPerThread; i++) {
						try {
							this.limiter.acquire(RateLimitOperation.MESSAGE_SEND, "42");
							allowed++;
						}
						catch (RateLimitExceededException ex) {
							// Expected once the limit is used up.
						}
					}
					return allowed;
				};
				results.add(executor.submit(call));
			}
			start.countDown();
			int allowed = 0;
			for (Future<Integer> result : results) {
				allowed += result.get(30, TimeUnit.SECONDS);
			}
			assertThat(allowed).isEqualTo(HUNDRED_PER_HOUR.maxRequests());
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void invalidLimitsAreRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> new Limit(0, Duration.ofMinutes(1)));
		assertThatIllegalArgumentException().isThrownBy(() -> new Limit(1, Duration.ZERO));
		assertThatIllegalArgumentException().isThrownBy(() -> new Limit(1, null));
		assertThatIllegalArgumentException().isThrownBy(() -> properties(0));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new RateLimitProperties(10, null, THREE_PER_MINUTE, HUNDRED_PER_HOUR, HUNDRED_PER_HOUR,
					HUNDRED_PER_HOUR, HUNDRED_PER_HOUR, HUNDRED_PER_HOUR, HUNDRED_PER_HOUR, HUNDRED_PER_HOUR,
					HUNDRED_PER_HOUR, HUNDRED_PER_HOUR, HUNDRED_PER_HOUR, HUNDRED_PER_HOUR, HUNDRED_PER_HOUR,
					HUNDRED_PER_HOUR))
			.withMessageContaining("conflux.rate-limit.login");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new RateLimitProperties(10, THREE_PER_MINUTE, THREE_PER_MINUTE, HUNDRED_PER_HOUR,
					HUNDRED_PER_HOUR, HUNDRED_PER_HOUR, HUNDRED_PER_HOUR, HUNDRED_PER_HOUR, HUNDRED_PER_HOUR,
					HUNDRED_PER_HOUR, HUNDRED_PER_HOUR, HUNDRED_PER_HOUR, HUNDRED_PER_HOUR, HUNDRED_PER_HOUR,
					HUNDRED_PER_HOUR, null))
			.withMessageContaining("conflux.rate-limit.admin-moderation");
	}

	@Test
	void everyOperationUsesItsOwnConfiguredLimit() {
		// Limit n allows n requests; each property gets a different n, in declaration order.
		Limit[] limits = new Limit[RateLimitOperation.values().length];
		for (int i = 0; i < limits.length; i++) {
			limits[i] = new Limit(i + 1, Duration.ofHours(1));
		}
		RateLimitProperties properties = new RateLimitProperties(100, limits[0], limits[1], limits[2], limits[3],
				limits[4], limits[5], limits[6], limits[7], limits[8], limits[9], limits[10], limits[11], limits[12],
				limits[13], limits[14]);

		assertThat(properties.limitFor(RateLimitOperation.LOGIN)).isSameAs(properties.login());
		assertThat(properties.limitFor(RateLimitOperation.REGISTER)).isSameAs(properties.register());
		assertThat(properties.limitFor(RateLimitOperation.LISTING_CREATE)).isSameAs(properties.listingCreate());
		assertThat(properties.limitFor(RateLimitOperation.LISTING_PUBLISH)).isSameAs(properties.listingPublish());
		assertThat(properties.limitFor(RateLimitOperation.LISTING_SAVE)).isSameAs(properties.listingSave());
		assertThat(properties.limitFor(RateLimitOperation.LISTING_INTEREST)).isSameAs(properties.listingInterest());
		assertThat(properties.limitFor(RateLimitOperation.MESSAGE_SEND)).isSameAs(properties.messageSend());
		assertThat(properties.limitFor(RateLimitOperation.REPORT_CREATE)).isSameAs(properties.reportCreate());
		assertThat(properties.limitFor(RateLimitOperation.LISTING_UPDATE)).isSameAs(properties.listingUpdate());
		assertThat(properties.limitFor(RateLimitOperation.LISTING_ARCHIVE)).isSameAs(properties.listingArchive());
		assertThat(properties.limitFor(RateLimitOperation.LISTING_UNSAVE)).isSameAs(properties.listingUnsave());
		assertThat(properties.limitFor(RateLimitOperation.CONNECTION_DECISION))
			.isSameAs(properties.connectionDecision());
		assertThat(properties.limitFor(RateLimitOperation.CONNECTION_WITHDRAW))
			.isSameAs(properties.connectionWithdraw());
		assertThat(properties.limitFor(RateLimitOperation.PROFILE_UPDATE)).isSameAs(properties.profileUpdate());
		assertThat(properties.limitFor(RateLimitOperation.ADMIN_MODERATION)).isSameAs(properties.adminModeration());

		// And every operation counts separately for the same subject.
		RateLimiter limiter = new RateLimiter(properties, this.nanos::get);
		for (RateLimitOperation operation : RateLimitOperation.values()) {
			int max = properties.limitFor(operation).maxRequests();
			for (int i = 0; i < max; i++) {
				limiter.acquire(operation, "7");
			}
			assertThatExceptionOfType(RateLimitExceededException.class)
				.isThrownBy(() -> limiter.acquire(operation, "7"));
		}
	}

	private void assertRejected(RateLimitOperation operation, String subject) {
		assertThatExceptionOfType(RateLimitExceededException.class)
			.isThrownBy(() -> this.limiter.acquire(operation, subject));
	}

	private void advance(Duration duration) {
		this.nanos.addAndGet(duration.toNanos());
	}

	// Login and registration: 3 per minute; the authenticated writes: 100 per hour.
	private static RateLimitProperties properties(int maxTrackedKeys) {
		return properties(maxTrackedKeys, THREE_PER_MINUTE);
	}

	private static RateLimitProperties properties(int maxTrackedKeys, Limit loginAndRegister) {
		return new RateLimitProperties(maxTrackedKeys, loginAndRegister, loginAndRegister, HUNDRED_PER_HOUR,
				HUNDRED_PER_HOUR, HUNDRED_PER_HOUR, HUNDRED_PER_HOUR, HUNDRED_PER_HOUR, HUNDRED_PER_HOUR,
				HUNDRED_PER_HOUR, HUNDRED_PER_HOUR, HUNDRED_PER_HOUR, HUNDRED_PER_HOUR, HUNDRED_PER_HOUR,
				HUNDRED_PER_HOUR, HUNDRED_PER_HOUR);
	}

}
