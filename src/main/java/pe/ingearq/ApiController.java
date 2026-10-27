package pe.ingearq;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final BusinessService business;

    public ApiController(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, BusinessService business) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.business = business;
    }

    @PostMapping("/auth/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body, HttpSession session) {
        String username = String.valueOf(body.getOrDefault("usuario", "")).trim().toLowerCase();
        String password = String.valueOf(body.getOrDefault("password", ""));
        List<Map<String, Object>> accounts = jdbc.queryForList(
                "SELECT id_usuario, nombres, apellidos, usuario, clave_hash, perfil, estado "
                        + "FROM usuarios WHERE usuario = ?",
                username);
        if (accounts.isEmpty()) throw invalidCredentials();

        Map<String, Object> account = accounts.get(0);
        if (!passwordEncoder.matches(password, String.valueOf(account.get("clave_hash")))) {
            throw invalidCredentials();
        }
        if (!"activo".equals(account.get("estado"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu usuario está dado de baja. Contacta al administrador.");
        }
        String role = "administrador".equals(account.get("perfil")) ? "admin" : "client";

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", account.get("id_usuario"));
        user.put("name", account.get("nombres"));
        user.put("surname", account.get("apellidos"));
        user.put("username", account.get("usuario"));
        user.put("role", role);
        session.setAttribute("user", user);
        return Map.of("user", user);
    }

    @GetMapping("/auth/session")
    public Map<String, Object> session(HttpSession session) {
        return Map.of("user", requireUser(session));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/catalogos/categorias")
    public List<Map<String, Object>> categories(HttpSession session) {
        requireUser(session);
        return business.getCategories();
    }

    @GetMapping("/catalogos/formas-pago")
    public List<Map<String, Object>> paymentMethods(HttpSession session) {
        requireUser(session);
        return business.getPaymentMethods();
    }

    @GetMapping("/productos")
    public List<Map<String, Object>> products(HttpSession session) {
        requireUser(session);
        return business.getProducts();
    }

    @PostMapping("/productos")
    public ResponseEntity<Map<String, Object>> createProduct(
            @RequestBody Map<String, Object> body,
            HttpSession session) {
        requireAdmin(session);
        long id = business.createProduct(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @PutMapping("/productos/{id}")
    public ResponseEntity<Void> updateProduct(
            @PathVariable long id,
            @RequestBody Map<String, Object> body,
            HttpSession session) {
        requireAdmin(session);
        business.updateProduct(id, body);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/productos/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable long id, HttpSession session) {
        requireAdmin(session);
        business.deactivateProduct(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/ventas")
    public List<Map<String, Object>> sales(HttpSession session) {
        requireUser(session);
        return business.getSales();
    }

    @PostMapping("/ventas")
    public ResponseEntity<Map<String, Object>> createSale(
        @RequestBody Map<String, Object> body,
        HttpSession session) {

    Map<String, Object> user = requireUser(session);
    long idTrabajador = ((Number) user.get("id")).longValue();

    return ResponseEntity.status(HttpStatus.CREATED)
            .body(business.createSale(body, idTrabajador));
}

    @PutMapping("/ventas/{id}")
    public ResponseEntity<Void> updateSale(
            @PathVariable String id,
            @RequestBody Map<String, Object> body,
            HttpSession session) {
        requireAdmin(session);
        business.updateSale(id, body);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/ventas/{id}")
    public ResponseEntity<Void> deleteSale(@PathVariable String id, HttpSession session) {
        requireAdmin(session);
        business.cancelSale(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/ventas/{id}/anular")
    public ResponseEntity<Void> cancelSale(@PathVariable String id, HttpSession session) {
        requireAdmin(session);
        business.cancelSale(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/caja/egresos")
    public ResponseEntity<Map<String, Object>> createExpense(
            @RequestBody Map<String, Object> body,
            HttpSession session) {
        requireAdmin(session);
        business.registerExpense(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("ok", true));
    }

    @GetMapping("/caja")
    public Map<String, Object> cash(HttpSession session) {
        requireUser(session);
        return business.getCashSummary();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requireUser(HttpSession session) {
        Object user = session.getAttribute("user");
        if (!(user instanceof Map<?, ?>)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión para continuar.");
        }
        return (Map<String, Object>) user;
    }

    private void requireAdmin(HttpSession session) {
        if (!"admin".equals(requireUser(session).get("role"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta acción requiere permisos de administrador.");
        }
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario o clave incorrectos.");
    }
}
