package com.example.app.core.service;

import com.example.app.core.repository.AuthTokenRepository;
import com.example.app.core.repository.UserRepository;
import com.example.app.core.security.TokenService;
import com.example.app.core.web.error.AuthException;
import com.example.app.domain.AuthToken;
import com.example.app.domain.BusinessTime;
import com.example.app.domain.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * The two short transactions before and after a login.
 *
 * <p>The slow password validation sits between them, outside any transaction ({@link AuthService#login}).
 * No DB connection or row lock is held while it runs. Transactions are started through the CDI interceptor,
 * so this is a separate bean from {@link AuthService} (a call within the same bean would not start one).
 */
@ApplicationScoped
public class LoginAttempts {

    static final int MAX_SESSIONS_PER_USER = 10;
    static final int MAX_FAILED_LOGINS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    @Inject
    private UserRepository userRepository;

    @Inject
    private AuthTokenRepository authTokenRepository;

    @Inject
    private TokenService tokenService;

    /** Creates the bean. CDI creates the single instance (it is application-scoped) and injects its fields. */
    public LoginAttempts() {}

    /**
     * Tells whether validating the password is worth it: the user exists, is enabled and is not locked.
     *
     * @param username the user name
     * @param now the current time
     * @return false for unknown, disabled and locked users
     */
    @Transactional
    public boolean accepts(String username, LocalDateTime now) {
        return userRepository
                .findByUsername(username)
                .filter(User::isEnabled)
                .filter(user -> !user.isLockedAt(now))
                .isPresent();
    }

    /**
     * Records the validation result under a lock on the user row, and creates a session on success.
     *
     * <p>Another attempt may have locked or disabled the user during validation, so the state is checked again here.
     * The failure count must be committed even when {@link AuthException} is thrown, hence {@code dontRollbackOn}.
     *
     * @param username the user name
     * @param passwordValid the result of the password validation
     * @return the user and the new session
     * @throws AuthException when authentication fails (401)
     */
    @Transactional(dontRollbackOn = AuthException.class)
    public Granted complete(String username, boolean passwordValid) {
        LocalDateTime now = BusinessTime.now();
        User user = userRepository
                .findByUsernameForUpdate(username)
                .filter(User::isEnabled)
                .orElseThrow(AuthException::invalidCredentials);
        if (user.isLockedAt(now)) {
            throw AuthException.invalidCredentials();
        }
        if (!passwordValid) {
            user.recordFailedLogin(now, MAX_FAILED_LOGINS, LOCK_DURATION);
            throw AuthException.invalidCredentials();
        }
        user.recordSuccessfulLogin();

        authTokenRepository.deleteExpired(now);
        authTokenRepository.deleteOldestBeyond(user, now, MAX_SESSIONS_PER_USER - 1);

        String refreshToken = tokenService.newToken();
        AuthToken session = AuthToken.issue(tokenService.hash(refreshToken), user, now);
        authTokenRepository.persist(session);
        return new Granted(user, session, refreshToken, now);
    }

    /**
     * The session created by a successful login.
     *
     * @param user the user
     * @param session the stored session (refresh-token row)
     * @param refreshToken the refresh token before hashing
     * @param issuedAt when the session was created
     */
    public record Granted(User user, AuthToken session, String refreshToken, LocalDateTime issuedAt) {}
}
