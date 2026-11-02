package com.example.app.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.app.core.web.error.AuthException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class LoginGateTest {

    @Test
    void runsTheLoginAndReturnsItsResult() {
        assertEquals("ok", new LoginGate(1, 100).run(() -> "ok"));
    }

    @Test
    void rejectsWith429WhenEverySlotStaysBusy() throws Exception {
        LoginGate gate = new LoginGate(1, 50);
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> busy = executor.submit(() -> gate.run(() -> {
                inside.countDown();
                await(release);
                return "first";
            }));
            inside.await(5, TimeUnit.SECONDS);

            AuthException rejected = assertThrows(AuthException.class, () -> gate.run(() -> "second"));
            assertEquals(429, rejected.getStatus());

            release.countDown();
            assertEquals("first", busy.get(5, TimeUnit.SECONDS));
            // The slot is returned even after a rejection.
            assertEquals("third", gate.run(() -> "third"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void returnsTheSlotWhenTheLoginFails() {
        LoginGate gate = new LoginGate(1, 50);
        assertThrows(
                AuthException.class,
                () -> gate.run(() -> {
                    throw AuthException.invalidCredentials();
                }));
        assertEquals("next", gate.run(() -> "next"));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
