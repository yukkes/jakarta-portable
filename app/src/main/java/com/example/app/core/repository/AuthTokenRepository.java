package com.example.app.core.repository;

import com.example.app.domain.AuthToken;
import com.example.app.domain.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Reads and writes refresh tokens ({@link AuthToken}). */
@ApplicationScoped
public class AuthTokenRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /** Creates the repository. For CDI; the entity manager is injected into a field. */
    public AuthTokenRepository() {}

    /**
     * Saves the token and assigns its ID at once.
     *
     * <p>The ID becomes the session ID of the access token, but EclipseLink does not assign an IDENTITY ID until flush
     * (Hibernate assigns it on INSERT), so this flushes.
     *
     * @param token a new token, not yet persisted
     */
    public void persist(AuthToken token) {
        entityManager.persist(token);
        entityManager.flush();
    }

    /**
     * Finds a token by its hash and reads its user along with it.
     *
     * <p>The fetch join has no alias: WildFly runs Hibernate in strict JPQL compliance mode, which rejects an aliased
     * fetch join such as {@code join fetch t.user u}.
     *
     * @param tokenHash the SHA-256 of the refresh token ({@code TokenService.hash})
     * @return the token with its user, or empty if no token has this hash
     */
    public Optional<AuthToken> findByTokenHash(String tokenHash) {
        return entityManager
                .createQuery(
                        "select t from AuthToken t join fetch t.user where t.tokenHash = :tokenHash", AuthToken.class)
                .setParameter("tokenHash", tokenHash)
                .getResultStream()
                .findFirst();
    }

    /**
     * Deletes the token and tells whether this call deleted it.
     *
     * <p>It is one bulk DELETE so that "usable only once" is atomic: two concurrent DELETEs are serialized by the row
     * lock and the loser deletes 0 rows. Reading and then calling {@code remove} would let both read it, and without
     * {@code @Version} neither would fail.
     *
     * @param tokenHash the SHA-256 of the refresh token
     * @return true for exactly one caller per token; false if it was already consumed or never existed
     */
    public boolean consume(String tokenHash) {
        return entityManager
                        .createQuery("delete from AuthToken t where t.tokenHash = :tokenHash")
                        .setParameter("tokenHash", tokenHash)
                        .executeUpdate()
                == 1;
    }

    /**
     * Deletes a token by ID, in one bulk DELETE. Nothing happens if it does not exist.
     *
     * @param id the token ID, which is also the session ID of the access token
     */
    public void deleteById(long id) {
        entityManager
                .createQuery("delete from AuthToken t where t.id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }

    /**
     * Deletes every token that expired before {@code now}, in one bulk DELETE.
     *
     * @param now the current time in {@code BusinessTime.zone()}
     */
    public void deleteExpired(LocalDateTime now) {
        entityManager
                .createQuery("delete from AuthToken t where t.expiresAt < :now")
                .setParameter("now", now)
                .executeUpdate();
    }

    /**
     * Deletes the user's active sessions except the newest {@code keep}.
     *
     * @param user the user whose sessions are trimmed
     * @param now the current time; sessions expired by then are not counted
     * @param keep how many of the newest sessions to keep
     */
    public void deleteOldestBeyond(User user, LocalDateTime now, int keep) {
        List<Long> ids = entityManager
                .createQuery(
                        "select t.id from AuthToken t where t.user = :user and t.expiresAt > :now"
                                + " order by t.createdAt desc, t.id desc",
                        Long.class)
                .setParameter("user", user)
                .setParameter("now", now)
                .getResultList();
        if (ids.size() > keep) {
            entityManager
                    .createQuery("delete from AuthToken t where t.id in :ids")
                    .setParameter("ids", ids.subList(keep, ids.size()))
                    .executeUpdate();
        }
    }
}
