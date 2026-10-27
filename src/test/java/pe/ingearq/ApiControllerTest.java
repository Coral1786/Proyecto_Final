package pe.ingearq;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

class ApiControllerTest {
    private final BusinessService business = mock(BusinessService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final ApiController controller = new ApiController(
            jdbc, passwordEncoder, business);

    @Test
    void rejectsProductsRequestWithoutSessionUser() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.products(new MockHttpSession()));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(business);
    }

    @Test
    void rejectsProductCreationForSeller() {
        MockHttpSession session = sessionWithRole("client");
        Map<String, Object> body = Map.of("name", "Tool", "category", "Tools");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.createProduct(body, session));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(business);
    }

    @Test
    void createsProductForAdministrator() {
        MockHttpSession session = sessionWithRole("admin");
        Map<String, Object> body = Map.of("name", "Tool", "category", "Tools");
        when(business.createProduct(body)).thenReturn(42L);

        var response = controller.createProduct(body, session);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(42L, response.getBody().get("id"));
        verify(business).createProduct(body);
    }

        @Test
        void normalizesUsernameAndStoresAuthenticatedAdministratorInSession() {
        Map<String, Object> account = Map.of(
            "id_usuario", 7L,
            "nombres", "Ada",
            "apellidos", "Lovelace",
            "usuario", "ada",
            "clave_hash", "encoded-password",
            "perfil", "administrador",
            "estado", "activo");
        when(jdbc.queryForList(anyString(), eq("ada")))
            .thenReturn(java.util.List.of(account));
        when(passwordEncoder.matches("secret", "encoded-password")).thenReturn(true);
        MockHttpSession session = new MockHttpSession();

        Map<String, Object> response = controller.login(
            Map.of("usuario", " ADA ", "password", "secret"), session);

        Map<?, ?> user = (Map<?, ?>) response.get("user");
        assertEquals("admin", user.get("role"));
        assertEquals("admin", ((Map<?, ?>) session.getAttribute("user")).get("role"));
        }

        @Test
        void rejectsLoginForInactiveUser() {
        Map<String, Object> account = Map.of(
            "id_usuario", 8L, "nombres", "Bob", "apellidos", "X", "usuario", "bob",
            "clave_hash", "h", "perfil", "cliente", "estado", "inactivo");
        when(jdbc.queryForList(anyString(), eq("bob"))).thenReturn(java.util.List.of(account));
        when(passwordEncoder.matches("secret", "h")).thenReturn(true);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> controller.login(Map.of("usuario", "bob", "password", "secret"), new MockHttpSession()));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        }

    private MockHttpSession sessionWithRole(String role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", Map.of("role", role));
        return session;
    }
}
