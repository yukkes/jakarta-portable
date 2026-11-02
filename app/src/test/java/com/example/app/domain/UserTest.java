package com.example.app.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** The account lock rules. {@code now} is passed as an argument, so they can be checked without a clock. */
class UserTest {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK = Duration.ofMinutes(15);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 12, 0);

    private final User user = new User("admin", "hash", "ADMIN");

    @Test
    void locksAfterTheConfiguredNumberOfFailures() {
        for (int attempt = 1; attempt < MAX_FAILURES; attempt++) {
            user.recordFailedLogin(NOW, MAX_FAILURES, LOCK);
            assertFalse(user.isLockedAt(NOW), "locked after " + attempt + " failures");
        }

        user.recordFailedLogin(NOW, MAX_FAILURES, LOCK);

        assertTrue(user.isLockedAt(NOW));
        assertTrue(user.isLockedAt(NOW.plus(LOCK).minusSeconds(1)));
        // The lock lifts exactly at lockedUntil (isLockedAt checks "after").
        assertFalse(user.isLockedAt(NOW.plus(LOCK)));
    }

    @Test
    void countsFromZeroAgainAfterALock() {
        lockTheAccount();
        assertTrue(user.isLockedAt(NOW));

        // Once the lock lifts, counting restarts. The next single failure does not lock.
        LocalDateTime later = NOW.plus(LOCK).plusMinutes(1);
        user.recordFailedLogin(later, MAX_FAILURES, LOCK);

        assertFalse(user.isLockedAt(later));
    }

    @Test
    void clearsTheFailureCountOnSuccess() {
        for (int attempt = 1; attempt < MAX_FAILURES; attempt++) {
            user.recordFailedLogin(NOW, MAX_FAILURES, LOCK);
        }
        user.recordSuccessfulLogin();

        // If the count had not been reset, this failure would be the 5th and lock the account.
        user.recordFailedLogin(NOW.plusSeconds(1), MAX_FAILURES, LOCK);

        assertFalse(user.isLockedAt(NOW.plusSeconds(1)));
    }

    @Test
    void countsTheFailuresAndRestartsWhenItLocks() {
        user.recordFailedLogin(NOW, MAX_FAILURES, LOCK);
        assertEquals(1, user.getFailedLoginCount());

        // The count goes back to 0 when the account locks.
        for (int attempt = 1; attempt < MAX_FAILURES; attempt++) {
            user.recordFailedLogin(NOW, MAX_FAILURES, LOCK);
        }
        assertEquals(0, user.getFailedLoginCount());
        assertTrue(user.isLockedAt(NOW));
    }

    private void lockTheAccount() {
        for (int attempt = 0; attempt < MAX_FAILURES; attempt++) {
            user.recordFailedLogin(NOW, MAX_FAILURES, LOCK);
        }
    }
}
