package pe.ingearq;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.password.PasswordEncoder;

class IngearqApplicationTest {
    private final IngearqApplication application = new IngearqApplication();

    @Test
    void applicationIsConfiguredAsSpringBootApplication() {
        assertTrue(IngearqApplication.class.isAnnotationPresent(SpringBootApplication.class));
    }

    @Test
    void passwordEncoderHashesAndVerifiesPasswords() {
        PasswordEncoder passwordEncoder = application.passwordEncoder();
        String encodedPassword = passwordEncoder.encode("unit-test-password");

        assertTrue(passwordEncoder.matches("unit-test-password", encodedPassword));
    }
}
