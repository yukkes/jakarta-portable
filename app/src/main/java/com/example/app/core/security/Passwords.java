package com.example.app.core.security;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.security.enterprise.identitystore.IdentityStoreHandler;
import jakarta.security.enterprise.identitystore.Pbkdf2PasswordHash;

/**
 * Password validation and hashing. No cryptography of its own: validation is left to the Jakarta Security
 * identity store ({@link IdentityStoreSetup}) and hashing to the container's {@link Pbkdf2PasswordHash}.
 */
@ApplicationScoped
public class Passwords {

    @Inject
    private IdentityStoreHandler identityStores;

    @Inject
    private Pbkdf2PasswordHash passwordHash;

    private String dummyHash;

    /** Creates the bean. CDI creates the single instance (it is application-scoped) and injects its fields. */
    public Passwords() {}

    @PostConstruct
    void init() {
        passwordHash.initialize(IdentityStoreSetup.hashParameters());
        dummyHash = passwordHash.generate("not-a-password".toCharArray());
    }

    /**
     * Tells whether the identity store accepts this user name and password.
     *
     * @param username the user name
     * @param password the password as typed
     * @return true if the password matches the stored hash
     */
    public boolean check(String username, String password) {
        CredentialValidationResult result = identityStores.validate(new UsernamePasswordCredential(username, password));
        return result.getStatus() == CredentialValidationResult.Status.VALID;
    }

    /**
     * Creates a hash in the specification's format with the parameters of {@link IdentityStoreSetup}.
     *
     * @param password the password to store
     * @return {@code PBKDF2WithHmacSHA256:600000:<salt>:<hash>}
     */
    public String hash(String password) {
        return passwordHash.generate(password.toCharArray());
    }

    /**
     * Does as much hashing as a real validation. Used for unknown, disabled and locked accounts, so the response time
     * does not tell them from a wrong password.
     *
     * @param password the password as typed; it is never accepted
     */
    public void checkAgainstNothing(String password) {
        passwordHash.verify(password.toCharArray(), dummyHash);
    }
}
