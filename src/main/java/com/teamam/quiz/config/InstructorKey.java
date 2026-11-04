package com.teamam.quiz.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The secret in the instructor's link, so she can run the dashboard from her own phone or laptop
 * through the internet link. Kept in a file, so the link keeps working after the app restarts;
 * delete the file and restart to make every old link stop working.
 */
@Component
public class InstructorKey {

    private final String key;

    public InstructorKey(@Value("${quiz.instructor-key-file:./data/instructor.key}") String keyFile) {
        Path path = Path.of(keyFile);
        try {
            if (Files.exists(path)) {
                key = Files.readString(path, StandardCharsets.UTF_8).trim();
            } else {
                byte[] random = new byte[32];
                new SecureRandom().nextBytes(random);
                key = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
                if (path.toAbsolutePath().getParent() != null) {
                    Files.createDirectories(path.toAbsolutePath().getParent());
                }
                Files.writeString(path, key, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read or create the instructor key file " + path, e);
        }
    }

    /** The key itself, for building the instructor's link. */
    public String value() {
        return key;
    }

    /** Is this the key? The comparison takes the same time however many characters match. */
    public boolean matches(String candidate) {
        return candidate != null && MessageDigest.isEqual(
                candidate.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8));
    }
}
