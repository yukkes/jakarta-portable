package com.example.app.quarkus;

import com.example.app.core.security.IdentityStoreSetup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.security.enterprise.credential.Credential;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.security.enterprise.identitystore.DatabaseIdentityStoreDefinition;
import jakarta.security.enterprise.identitystore.IdentityStoreHandler;
import jakarta.security.enterprise.identitystore.Pbkdf2PasswordHash;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.glassfish.soteria.identitystores.hash.Pbkdf2PasswordHashImpl;

/**
 * Provides the two beans the application uses, for Quarkus, which does not implement Jakarta Security.
 * On the EE servers the server itself provides them.
 *
 * <ul>
 *   <li>{@link Pbkdf2PasswordHash}: the reference implementation's (Soteria) class, used as-is. It hashes with the same
 *       code as GlassFish and Payara instead of a separate implementation.</li>
 *   <li>{@link IdentityStoreHandler}: runs the {@code callerQuery} of the application's
 *       {@link DatabaseIdentityStoreDefinition} ({@link IdentityStoreSetup}). The query and hash parameters live in one
 *       place for every runtime.</li>
 * </ul>
 *
 * <p>Supports only the user name and password validation the application uses (no groups, since the
 * definition has {@code useFor = VALIDATE}).
 */
@ApplicationScoped
public class QuarkusIdentityStoreHandler implements IdentityStoreHandler {

    private static final DatabaseIdentityStoreDefinition DEFINITION =
            IdentityStoreSetup.class.getAnnotation(DatabaseIdentityStoreDefinition.class);

    @Inject
    DataSource dataSource;

    @Inject
    Pbkdf2PasswordHash passwordHash;

    @Override
    public CredentialValidationResult validate(Credential credential) {
        if (!(credential instanceof UsernamePasswordCredential login)) {
            return CredentialValidationResult.NOT_VALIDATED_RESULT;
        }
        String stored = storedHash(login.getCaller());
        if (stored == null || !passwordHash.verify(login.getPassword().getValue(), stored)) {
            return CredentialValidationResult.INVALID_RESULT;
        }
        return new CredentialValidationResult(login.getCaller());
    }

    private String storedHash(String caller) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement(DEFINITION.callerQuery())) {
            query.setString(1, caller);
            try (ResultSet row = query.executeQuery()) {
                return row.next() ? row.getString(1) : null;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("identity store query failed", exception);
        }
    }

    /** Soteria's implementation, initialized with the same definition parameters as on the EE servers. */
    @Produces
    @Dependent
    static Pbkdf2PasswordHash pbkdf2PasswordHash() {
        Pbkdf2PasswordHash hash = new Pbkdf2PasswordHashImpl();
        hash.initialize(IdentityStoreSetup.hashParameters());
        return hash;
    }
}
