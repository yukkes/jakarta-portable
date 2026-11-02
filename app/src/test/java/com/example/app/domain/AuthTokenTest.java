package com.example.app.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AuthTokenTest {

    private static final LocalDateTime LOGIN = LocalDateTime.of(2026, 1, 1, 9, 0);

    private final AuthToken token = AuthToken.issue("hash", new User("admin", "hash", "ADMIN"), LOGIN);

    @Test
    void isUsableUntilTheIdleTimeout() {
        assertTrue(token.isUsableAt(LOGIN));
        // Exactly at the boundary it is still usable.
        assertTrue(token.isUsableAt(LOGIN.plus(AuthToken.IDLE_TIMEOUT)));
        assertFalse(token.isUsableAt(LOGIN.plus(AuthToken.IDLE_TIMEOUT).plusSeconds(1)));
    }

    @Test
    void rotationRestartsTheIdleTimeoutButKeepsTheAbsoluteExpiry() {
        LocalDateTime later = LOGIN.plusMinutes(20);

        AuthToken next = token.rotate("next", later);

        assertEquals(token.getExpiresAt(), next.getExpiresAt());
        assertTrue(next.isUsableAt(later.plus(AuthToken.IDLE_TIMEOUT)));
    }

    @Test
    void cannotBeExtendedPastTheAbsoluteLifetime() {
        // Even when refreshed within every 30 minutes, it stops working 8 hours after login.
        AuthToken current = token;
        LocalDateTime now = LOGIN;
        while (now.isBefore(LOGIN.plus(AuthToken.ABSOLUTE_LIFETIME))) {
            now = now.plusMinutes(20);
            current = current.rotate("hash-" + now, now);
        }

        assertFalse(current.isUsableAt(now));
        assertEquals(LOGIN.plus(AuthToken.ABSOLUTE_LIFETIME), current.getExpiresAt());
    }
}
