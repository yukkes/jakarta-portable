package com.example.app.core.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.security.enterprise.identitystore.DatabaseIdentityStoreDefinition;
import jakarta.security.enterprise.identitystore.IdentityStore.ValidationType;
import jakarta.security.enterprise.identitystore.Pbkdf2PasswordHash;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Definition of the Jakarta Security database identity store that validates passwords. The EE servers (Liberty,
 * WildFly, Payara, Payara Micro, GlassFish) read this annotation and run the identity store.
 *
 * <p>The identity store uses the same default data source as JPA ({@code java:comp/DefaultDataSource}) and only
 * validates ({@link ValidationType#VALIDATE}). Roles come from the {@code User} entity along with the lock state.
 *
 * <p>Quarkus does not implement Jakarta Security, so {@code QuarkusIdentityStoreHandler} in the {@code quarkus}
 * module reads the same annotation and runs the query. The hash format
 * ({@code algorithm:iterations:salt:hash}) is fixed by the specification, so a hash created on any runtime
 * validates on every other runtime.
 */
@ApplicationScoped
@DatabaseIdentityStoreDefinition(
        callerQuery = "select password_hash from app_users where username = ?",
        useFor = ValidationType.VALIDATE,
        hashAlgorithm = Pbkdf2PasswordHash.class,
        hashAlgorithmParameters = {
            IdentityStoreSetup.ALGORITHM,
            IdentityStoreSetup.ITERATIONS,
            IdentityStoreSetup.SALT_BYTES,
            IdentityStoreSetup.KEY_BYTES
        })
public class IdentityStoreSetup {

    // 600,000 iterations, as recommended by OWASP (2023) for PBKDF2-HMAC-SHA256.
    static final String ALGORITHM = "Pbkdf2PasswordHash.Algorithm=PBKDF2WithHmacSHA256";
    static final String ITERATIONS = "Pbkdf2PasswordHash.Iterations=600000";
    static final String SALT_BYTES = "Pbkdf2PasswordHash.SaltSizeBytes=16";
    static final String KEY_BYTES = "Pbkdf2PasswordHash.KeySizeBytes=32";

    /** Creates the bean that carries the definition. The runtimes read its annotation; the instance does nothing. */
    public IdentityStoreSetup() {}

    /**
     * Returns the hash parameters of the annotation, in the form {@link Pbkdf2PasswordHash#initialize} takes.
     *
     * @return parameter name to value
     */
    public static Map<String, String> hashParameters() {
        return Arrays.stream(new String[] {ALGORITHM, ITERATIONS, SALT_BYTES, KEY_BYTES})
                .map(parameter -> parameter.split("=", 2))
                .collect(Collectors.toUnmodifiableMap(pair -> pair[0], pair -> pair[1]));
    }
}
