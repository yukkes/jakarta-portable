package com.example.app.core.batch;

import com.example.app.core.security.SecuritySettings;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Resolves an import file name to a path inside the configured import directory.
 *
 * <p>Only a file name without directories is accepted, and the resolved real path must also lie inside the
 * directory. Neither {@code ../} nor a symbolic link can read any other file on the server.
 */
@ApplicationScoped
public class ImportFileResolver {

    private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9._-]{1,100}\\.csv");

    @Inject
    private SecuritySettings settings;

    /** Creates the resolver. CDI creates the single instance and injects its fields. */
    public ImportFileResolver() {}

    /**
     * Resolves a file name to a readable file inside the import directory.
     *
     * @param fileName a bare {@code .csv} name
     * @return the real path of the file
     * @throws IllegalArgumentException when the file name is not allowed or the file does not exist
     */
    public Path resolve(String fileName) {
        if (fileName == null || !FILE_NAME.matcher(fileName).matches()) {
            throw new IllegalArgumentException(
                    "Specify the input file as a .csv file name made of letters, digits, '.', '_' and '-' only");
        }

        Path base = settings.importDir().toAbsolutePath().normalize();
        Path candidate = base.resolve(fileName).normalize();
        if (!candidate.startsWith(base) || !Files.isRegularFile(candidate)) {
            throw notFound(fileName);
        }
        try {
            Path real = candidate.toRealPath();
            if (!real.startsWith(base.toRealPath())) {
                throw notFound(fileName);
            }
            return real;
        } catch (IOException exception) {
            throw notFound(fileName);
        }
    }

    private IllegalArgumentException notFound(String fileName) {
        return new IllegalArgumentException("Input file not found in the import directory: " + fileName);
    }
}
