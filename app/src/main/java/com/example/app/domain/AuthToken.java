package com.example.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * The session of one refresh token. The token itself is not stored, only its SHA-256 hash.
 *
 * <p>Each rotation creates a new row, so {@code createdAt} doubles as "last used".
 * {@code expiresAt} is the absolute expiry fixed at login and carried over to the rotated rows unchanged.
 * Even with continuous use, a new login is required {@link #ABSOLUTE_LIFETIME} after logging in.
 */
@Entity
@Table(
        name = "auth_tokens",
        indexes = {@Index(name = "idx_auth_tokens_expires", columnList = "expires_at")})
public class AuthToken {

    /** Absolute lifetime from login. */
    public static final Duration ABSOLUTE_LIFETIME = Duration.ofHours(8);

    /** Expires when unused for this long since the last rotation. */
    public static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Creates an empty instance. For JPA, which creates instances when it loads rows. */
    protected AuthToken() {}

    private AuthToken(String tokenHash, User user, LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.user = user;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /**
     * Creates a new session at login. Its absolute expiry is {@link #ABSOLUTE_LIFETIME} after {@code now}.
     *
     * @param tokenHash the hash of the refresh token
     * @param user the user who logged in
     * @param now the login time
     * @return the session, not yet persisted
     */
    public static AuthToken issue(String tokenHash, User user, LocalDateTime now) {
        return new AuthToken(tokenHash, user, now, now.plus(ABSOLUTE_LIFETIME));
    }

    /**
     * Creates the row that replaces this one after a refresh. It keeps the user and the absolute expiry, so refreshing
     * never extends the session.
     *
     * @param nextTokenHash the hash of the next refresh token
     * @param now the refresh time
     * @return the new session, not yet persisted
     */
    public AuthToken rotate(String nextTokenHash, LocalDateTime now) {
        return new AuthToken(nextTokenHash, user, now, expiresAt);
    }

    /**
     * Tells whether the session is still usable: before its absolute expiry and within the idle timeout.
     *
     * @param now the current time
     * @return true if a refresh may use it
     */
    public boolean isUsableAt(LocalDateTime now) {
        return expiresAt.isAfter(now) && !createdAt.plus(IDLE_TIMEOUT).isBefore(now);
    }

    /**
     * Returns the ID, which is also the session ID in the access token.
     *
     * @return the ID, or null until the entity is first flushed
     */
    public Long getId() {
        return id;
    }

    /**
     * Returns the user the session belongs to (loaded lazily).
     *
     * @return the user
     */
    public User getUser() {
        return user;
    }

    /**
     * Returns the absolute expiry of the session.
     *
     * @return the expiry
     */
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}
