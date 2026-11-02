package com.example.app.core.security;

import com.example.app.core.web.error.AuthException;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Limits the number of logins processed concurrently across the whole server.
 *
 * <p>Every login, successful or not, computes 600,000 iterations of PBKDF2 once (a dummy one for unknown users).
 * Without a limit, a flood of login requests turns straight into parallel hashing and uses up every core.
 * Account lockout does not help against attempts spread over many user names. Here concurrent hashing is limited
 * to half the cores, and 429 is returned if no slot frees up within {@link #WAIT_MILLIS}.
 *
 * <p>Jakarta REST has no portable way to read the client address (the Servlet API is not used), so the
 * limit is global rather than per client. Per-client limits belong in the proxy in front.
 */
@ApplicationScoped
public class LoginGate {

    static final long WAIT_MILLIS = 2_000;

    private final Semaphore slots;
    private final long waitMillis;

    /** Creates the gate. Half the available cores (at least one) may hash at the same time. */
    public LoginGate() {
        this(Math.max(1, Runtime.getRuntime().availableProcessors() / 2), WAIT_MILLIS);
    }

    LoginGate(int slots, long waitMillis) {
        this.slots = new Semaphore(slots, true);
        this.waitMillis = waitMillis;
    }

    /**
     * Takes a slot and runs the login, waiting at most {@link #WAIT_MILLIS} for one.
     *
     * @param <T> the login result type
     * @param login the login, which hashes the password
     * @return the login's result
     * @throws AuthException if no slot frees up in time (429)
     */
    public <T> T run(Supplier<T> login) {
        boolean acquired;
        try {
            acquired = slots.tryAcquire(waitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            acquired = false;
        }
        if (!acquired) {
            throw AuthException.tooManyLogins();
        }
        try {
            return login.get();
        } finally {
            slots.release();
        }
    }
}
