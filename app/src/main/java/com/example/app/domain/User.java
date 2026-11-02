package com.example.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * A login user and the account lock state.
 *
 * <p>The Jakarta Security identity store validates passwords by reading the {@code password_hash} column directly
 * ({@code IdentityStoreSetup}). This class only stores the hash and never validates.
 */
@Entity
@Table(
        name = "app_users",
        uniqueConstraints = @UniqueConstraint(name = "uk_app_users_username", columnNames = "username"))
public class User extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    /** In the {@code Pbkdf2PasswordHash} format ({@code algorithm:iterations:salt:hash}). */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    /** Creates an empty instance. For JPA, which creates instances when it loads rows. */
    protected User() {}

    /**
     * Creates an enabled user with no failed logins.
     *
     * @param username the unique user name
     * @param passwordHash a {@code Pbkdf2PasswordHash} hash, never the password
     * @param role {@code USER} or {@code ADMIN}
     */
    public User(String username, String passwordHash, String role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    /**
     * Returns the ID, assigned by the database.
     *
     * @return the ID, or null until the entity is first flushed
     */
    public Long getId() {
        return id;
    }

    /**
     * Returns the user name.
     *
     * @return the unique user name
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns the role.
     *
     * @return {@code USER} or {@code ADMIN}
     */
    public String getRole() {
        return role;
    }

    /**
     * Tells whether the user may log in at all.
     *
     * @return false for a disabled user
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns the failed logins since the last success or lockout.
     *
     * @return the count
     */
    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    /**
     * Tells whether a lockout set by {@link #recordFailedLogin} is still running at {@code now}.
     *
     * @param now the current time
     * @return true while locked
     */
    public boolean isLockedAt(LocalDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Counts a failure, and on the {@code maxFailures}-th locks the account for {@code lockDuration}.
     *
     * @param now the time of the failure
     * @param maxFailures the failures that lock the account
     * @param lockDuration how long the lockout lasts
     */
    public void recordFailedLogin(LocalDateTime now, int maxFailures, Duration lockDuration) {
        failedLoginCount++;
        if (failedLoginCount >= maxFailures) {
            lockedUntil = now.plus(lockDuration);
            failedLoginCount = 0;
        }
    }

    /** Resets the failure count and lifts any lockout. */
    public void recordSuccessfulLogin() {
        failedLoginCount = 0;
        lockedUntil = null;
    }
}
