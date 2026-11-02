package com.example.app.core.service;

import com.example.app.core.repository.AuthTokenRepository;
import com.example.app.core.security.Passwords;
import com.example.app.core.security.SecuritySettings;
import com.example.app.core.security.TokenService;
import com.example.app.core.web.error.AuthException;
import com.example.app.domain.AuthToken;
import com.example.app.domain.BusinessTime;
import com.example.app.domain.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Authentication with two tokens: an access token and a refresh token.
 *
 * <ul>
 *   <li><b>Access token</b>: an HS256 JWT. It is verified by its signature alone, without the DB, so requests
 *       cost no DB query. It cannot be revoked, so its lifetime is short instead (10 minutes by default).</li>
 *   <li><b>Refresh token</b>: an unguessable random value; the DB stores only its hash.
 *       It is used only on {@code /api/auth/refresh} and rotated on every use. For its expiry see
 *       {@link AuthToken}.</li>
 * </ul>
 */
@ApplicationScoped
public class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class.getName());

    @Inject
    private AuthTokenRepository authTokenRepository;

    @Inject
    private LoginAttempts loginAttempts;

    @Inject
    private Passwords passwords;

    @Inject
    private TokenService tokenService;

    @Inject
    private SecuritySettings settings;

    /** Creates the service. CDI creates the single instance (it is application-scoped) and injects its fields. */
    public AuthService() {}

    /**
     * Logs in with a user name and password.
     *
     * <p>Unknown, disabled and locked accounts do the same amount of hashing and get the same 401, so the response does
     * not reveal which case applied. Password validation (PBKDF2) runs outside a transaction; only the short steps of
     * {@link LoginAttempts} before and after it hold a DB connection and a row lock.
     *
     * @param username the user name
     * @param password the password as typed
     * @return the access token, the refresh token and the session
     * @throws AuthException when authentication fails (401)
     */
    public LoginResult login(String username, String password) {
        if (!loginAttempts.accepts(username, BusinessTime.now())) {
            passwords.checkAgainstNothing(password);
            throw AuthException.invalidCredentials();
        }
        boolean valid = passwords.check(username, password);
        LoginAttempts.Granted granted = loginAttempts.complete(username, valid);
        return issue(granted.user(), granted.session(), granted.refreshToken(), granted.issuedAt());
    }

    /**
     * Exchanges a refresh token for a new access token and the next refresh token.
     *
     * <p>The old token is removed with a conditional DELETE, so of several concurrent requests with the same token
     * exactly one gets a new session.
     *
     * @param refreshToken the refresh token as sent
     * @return empty when the exchange failed (unknown, expired, disabled user, or lost the race)
     */
    @Transactional
    public Optional<LoginResult> refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        LocalDateTime now = BusinessTime.now();
        String tokenHash = tokenService.hash(refreshToken);
        Optional<AuthToken> found = authTokenRepository.findByTokenHash(tokenHash);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        AuthToken current = found.get();
        if (!authTokenRepository.consume(tokenHash)) {
            // The OAuth 2.0 Security BCP (RFC 9700) revokes the whole token family here, but this sample only
            // logs it, so that concurrent refreshes from several tabs do not log the user out.
            LOG.warning("refresh token reused concurrently; the other request won");
            return Optional.empty();
        }
        if (!current.getUser().isEnabled() || !current.isUsableAt(now)) {
            return Optional.empty();
        }

        String nextRefreshToken = tokenService.newToken();
        AuthToken next = current.rotate(tokenService.hash(nextRefreshToken), now);
        authTokenRepository.persist(next);
        return Optional.of(issue(current.getUser(), next, nextRefreshToken, now));
    }

    /**
     * Verifies the signature and expiry of an access token. Does not touch the database.
     *
     * @param accessToken the token as sent
     * @return the session it represents, or empty if it is invalid or expired
     */
    public Optional<AuthSession> authenticate(String accessToken) {
        return tokenService
                .parseAccessToken(settings.tokenSecret(), accessToken)
                .map(token -> new AuthSession(
                        new AuthenticatedUser(token.userId(), token.username(), token.roles()),
                        token.csrfToken(),
                        token.sessionId()));
    }

    /**
     * Ends the session and revokes the refresh token.
     *
     * <p>The session ID in an access token is the ID of the refresh-token row, so Bearer clients without a cookie can
     * revoke it as well. The access token itself stays valid until it expires.
     *
     * @param current the session of the verified access token
     * @param refreshToken the refresh token from the cookie, or null for Bearer clients
     */
    @Transactional
    public void logout(AuthSession current, String refreshToken) {
        authTokenRepository.deleteById(current.sessionId());
        if (refreshToken != null && !refreshToken.isBlank()) {
            authTokenRepository.consume(tokenService.hash(refreshToken));
        }
    }

    private LoginResult issue(User user, AuthToken session, String refreshToken, LocalDateTime now) {
        Instant issuedAt = now.atZone(BusinessTime.zone()).toInstant();
        Instant expiresAt = issuedAt.plus(settings.accessTokenTtl());
        String csrfToken = tokenService.newCsrfToken();
        Set<String> roles = Set.of(user.getRole());
        String accessToken = tokenService.accessToken(
                settings.tokenSecret(),
                user.getId(),
                user.getUsername(),
                roles,
                session.getId(),
                csrfToken,
                issuedAt,
                expiresAt);
        return new LoginResult(
                accessToken,
                refreshToken,
                csrfToken,
                LocalDateTime.ofInstant(expiresAt, BusinessTime.zone()),
                session.getExpiresAt(),
                new AuthenticatedUser(user.getId(), user.getUsername(), roles));
    }

    /**
     * The logged-in user.
     *
     * @param id the user ID
     * @param username the user name
     * @param roles the user's roles
     */
    public record AuthenticatedUser(Long id, String username, Set<String> roles) {}

    /**
     * The session represented by a verified access token.
     *
     * @param user the user
     * @param csrfToken the CSRF token of the session
     * @param sessionId the session (refresh-token row) ID
     */
    public record AuthSession(AuthenticatedUser user, String csrfToken, long sessionId) {}

    /**
     * The result of a login or a refresh.
     *
     * @param accessToken the new access token
     * @param refreshToken the new refresh token, before hashing
     * @param csrfToken the CSRF token of the session
     * @param expiresAt expiry of the access token
     * @param sessionExpiresAt absolute expiry of the session (refresh token); used as the cookie lifetime
     * @param user the user
     */
    public record LoginResult(
            String accessToken,
            String refreshToken,
            String csrfToken,
            LocalDateTime expiresAt,
            LocalDateTime sessionExpiresAt,
            AuthenticatedUser user) {}
}
