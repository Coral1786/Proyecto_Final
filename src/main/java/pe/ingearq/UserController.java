package pe.ingearq;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
public class UserController {
    private static final Pattern DNI = Pattern.compile("\\d{8}");
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9_.]{4,50}");
    private static final Set<String> PROFILES = Set.of("cliente", "administrador");
    private static final Set<String> STATUSES = Set.of("activo", "inactivo");

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public UserController(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> body, HttpSession session) {
        requireAdmin(session);
        long id = insertUser(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @GetMapping("/usuarios")
    public List<Map<String, Object>> list(HttpSession session) {
        requireAdmin(session);
        return jdbc.query(
                "SELECT id_usuario, dni, nombres, apellidos, usuario, perfil, estado FROM usuarios "
                        + "ORDER BY apellidos, nombres",
                (rs, row) -> {
                    Map<String, Object> user = new LinkedHashMap<>();
                    user.put("id", rs.getLong("id_usuario"));
                    user.put("dni", rs.getString("dni"));
                    user.put("names", rs.getString("nombres"));
                    user.put("surnames", rs.getString("apellidos"));
                    user.put("username", rs.getString("usuario"));
                    user.put("profile", rs.getString("perfil"));
                    user.put("status", rs.getString("estado"));
                    return user;
                });
    }

    @PostMapping("/usuarios")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body, HttpSession session) {
        requireAdmin(session);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", insertUser(body)));
    }

    @PutMapping("/usuarios/{id}")
    public ResponseEntity<Void> update(
            @PathVariable long id,
            @RequestBody Map<String, Object> body,
            HttpSession session) {
        long currentId = requireAdmin(session);
        String dni = dni(body);
        String names = text(body, "names", "nombres");
        String surnames = text(body, "surnames", "apellidos");
        String username = username(body);
        String profile = choice(body, "profile", PROFILES, "perfil");
        String status = choice(body, "status", STATUSES, "estado");
        String password = String.valueOf(body.getOrDefault("password", ""));
        if (id == currentId && (!"administrador".equals(profile) || !"activo".equals(status))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No puedes quitarte el perfil de administrador ni darte de baja.");
        }
        requireUnique(dni, username, id);
        try {
            int changed = jdbc.update(
                    "UPDATE usuarios SET dni = ?, nombres = ?, apellidos = ?, usuario = ?, perfil = ?, estado = ? "
                            + "WHERE id_usuario = ?",
                    dni, names, surnames, username, profile, status, id);
            if (changed == 0 && !exists(id)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado.");
            }
            if (!password.isEmpty()) {
                jdbc.update("UPDATE usuarios SET clave_hash = ? WHERE id_usuario = ?",
                        passwordEncoder.encode(password(password)), id);
            }
        } catch (DuplicateKeyException exception) {
            throw duplicate(exception);
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/usuarios/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable long id, HttpSession session) {
        if (requireAdmin(session) == id) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No puedes darte de baja a ti mismo.");
        }
        int changed = jdbc.update("UPDATE usuarios SET estado = 'inactivo' WHERE id_usuario = ?", id);
        if (changed == 0 && !exists(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado.");
        }
        return ResponseEntity.noContent().build();
    }

    private long insertUser(Map<String, Object> body) {
        String dni = dni(body);
        String names = text(body, "names", "nombres");
        String surnames = text(body, "surnames", "apellidos");
        String username = username(body);
        String password = password(String.valueOf(body.getOrDefault("password", "")));
        String profile = choice(body, "profile", PROFILES, "perfil");
        String status = body.get("status") == null ? "activo" : choice(body, "status", STATUSES, "estado");
        requireUnique(dni, username, 0);
        try {
            jdbc.update(
                    "INSERT INTO usuarios (dni, nombres, apellidos, usuario, clave_hash, perfil, estado) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    dni, names, surnames, username, passwordEncoder.encode(password), profile, status);
        } catch (DuplicateKeyException exception) {
            throw duplicate(exception);
        }
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void requireUnique(String dni, String username, long excludeId) {
        Integer byDni = jdbc.queryForObject(
                "SELECT COUNT(*) FROM usuarios WHERE dni = ? AND id_usuario <> ?", Integer.class, dni, excludeId);
        if (byDni != null && byDni > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un usuario registrado con ese DNI.");
        }
        Integer byUsername = jdbc.queryForObject(
                "SELECT COUNT(*) FROM usuarios WHERE usuario = ? AND id_usuario <> ?", Integer.class, username, excludeId);
        if (byUsername != null && byUsername > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El nombre de usuario ya existe. Elige otro.");
        }
    }

    private ResponseStatusException duplicate(DuplicateKeyException exception) {
        String message = String.valueOf(exception.getMessage());
        return new ResponseStatusException(HttpStatus.CONFLICT, message.contains("uq_usuarios_dni")
                ? "Ya existe un usuario registrado con ese DNI."
                : "El nombre de usuario ya existe. Elige otro.");
    }

    private boolean exists(long id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE id_usuario = ?", Integer.class, id);
        return count != null && count > 0;
    }

    private String dni(Map<String, Object> body) {
        String dni = String.valueOf(body.getOrDefault("dni", "")).trim();
        if (!DNI.matcher(dni).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El DNI debe tener exactamente 8 dígitos.");
        }
        return dni;
    }

    private String username(Map<String, Object> body) {
        String username = String.valueOf(body.getOrDefault("username", "")).trim().toLowerCase();
        if (!USERNAME.matcher(username).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El usuario debe tener de 4 a 50 caracteres (letras, números, punto o guion bajo).");
        }
        return username;
    }

    private String password(String password) {
        if (password.length() < 8 || password.length() > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La clave debe tener entre 8 y 72 caracteres.");
        }
        return password;
    }

    private String text(Map<String, Object> body, String key, String label) {
        String value = String.valueOf(body.getOrDefault(key, "")).trim();
        if (value.isEmpty() || value.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los " + label + " son obligatorios (máximo 100 caracteres).");
        }
        return value;
    }

    private String choice(Map<String, Object> body, String key, Set<String> allowed, String label) {
        String value = String.valueOf(body.getOrDefault(key, "")).trim().toLowerCase();
        if (!allowed.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El " + label + " no es válido.");
        }
        return value;
    }

    private long requireAdmin(HttpSession session) {
        Object user = session.getAttribute("user");
        if (!(user instanceof Map<?, ?> map)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Inicia sesión para continuar.");
        }
        if (!"admin".equals(map.get("role"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta acción requiere permisos de administrador.");
        }
        return ((Number) map.get("id")).longValue();
    }
}
