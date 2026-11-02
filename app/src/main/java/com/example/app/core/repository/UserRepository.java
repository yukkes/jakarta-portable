package com.example.app.core.repository;

import com.example.app.domain.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;

/** Reads and writes users. */
@ApplicationScoped
public class UserRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /** Creates the repository. For CDI; the entity manager is injected into a field. */
    public UserRepository() {}

    /**
     * Finds a user by name, without a lock.
     *
     * @param username the user name
     * @return the user, or empty if no user has this name
     */
    public Optional<User> findByUsername(String username) {
        return find(username, LockModeType.NONE);
    }

    /**
     * Finds a user by name and locks the row until the end of the transaction, so that concurrent logins of one user
     * never lose a failure count.
     *
     * @param username the user name
     * @return the locked user, or empty if no user has this name
     */
    public Optional<User> findByUsernameForUpdate(String username) {
        return find(username, LockModeType.PESSIMISTIC_WRITE);
    }

    /**
     * Counts the users; the seeder creates the configured users only when there are none.
     *
     * @return the number of users
     */
    public long count() {
        return entityManager
                .createQuery("select count(u) from User u", Long.class)
                .getSingleResult();
    }

    /**
     * Inserts a user at the next flush or commit; a duplicate user name fails there.
     *
     * @param user a user not yet persisted
     */
    public void persist(User user) {
        entityManager.persist(user);
    }

    /**
     * Finds a user by name with the given lock.
     *
     * @param username the user name
     * @param lock the lock mode
     * @return the user, or empty if no user has this name
     */
    private Optional<User> find(String username, LockModeType lock) {
        return entityManager
                .createQuery("select u from User u where u.username = :username", User.class)
                .setParameter("username", username)
                .setLockMode(lock)
                .getResultStream()
                .findFirst();
    }
}
