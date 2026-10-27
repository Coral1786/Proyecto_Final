package pe.ingearq;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class IngearqApplication {
    public static void main(String[] args) {
        SpringApplication.run(IngearqApplication.class, args);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    @SuppressWarnings("unused")
    CommandLineRunner initializeDatabase(
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            @Value("${app.seed-demo-data:true}") boolean seedDemoData,
            @Value("${app.demo.admin-user:admin}") String adminUser,
            @Value("${app.demo.admin-password}") String adminPassword,
            @Value("${app.demo.client-user:vendedor}") String clientUser,
            @Value("${app.demo.seller-password}") String clientPassword) {
        return args -> {
            addColumnIfMissing(jdbc, "productos", "activo", "TINYINT(1) NOT NULL DEFAULT 1");
            addColumnIfMissing(jdbc, "caja", "descripcion", "VARCHAR(255) NULL");
            addColumnIfMissing(jdbc, "ventas", "id_trabajador", "INT UNSIGNED NULL");
            addForeignKeyIfMissing(
                    jdbc,
                    "ventas",
                    "fk_ventas_trabajador",
                    "ALTER TABLE ventas ADD CONSTRAINT fk_ventas_trabajador "
                            + "FOREIGN KEY (id_trabajador) REFERENCES trabajadores (id_trabajador)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS usuarios ("
                    + "id_usuario INT UNSIGNED NOT NULL AUTO_INCREMENT, "
                    + "dni CHAR(8) NOT NULL, "
                    + "nombres VARCHAR(100) NOT NULL, "
                    + "apellidos VARCHAR(100) NOT NULL, "
                    + "usuario VARCHAR(50) NOT NULL, "
                    + "clave_hash VARCHAR(255) NOT NULL, "
                    + "perfil ENUM('cliente', 'administrador') NOT NULL DEFAULT 'cliente', "
                    + "estado ENUM('activo', 'inactivo') NOT NULL DEFAULT 'activo', "
                    + "PRIMARY KEY (id_usuario), "
                    + "UNIQUE KEY uq_usuarios_dni (dni), "
                    + "UNIQUE KEY uq_usuarios_usuario (usuario)) ENGINE=InnoDB");
            jdbc.execute("CREATE TABLE IF NOT EXISTS categorias ("
                    + "id_categoria INT UNSIGNED NOT NULL AUTO_INCREMENT, "
                    + "nombre VARCHAR(100) NOT NULL, "
                    + "PRIMARY KEY (id_categoria), UNIQUE KEY uq_categorias_nombre (nombre)) ENGINE=InnoDB");
            jdbc.execute("CREATE TABLE IF NOT EXISTS formas_pago ("
                    + "id_forma_pago INT UNSIGNED NOT NULL AUTO_INCREMENT, "
                    + "nombre VARCHAR(50) NOT NULL, "
                    + "PRIMARY KEY (id_forma_pago), UNIQUE KEY uq_formas_pago_nombre (nombre)) ENGINE=InnoDB");
            addColumnIfMissing(jdbc, "ventas", "id_usuario", "INT UNSIGNED NULL");
            addForeignKeyIfMissing(
                    jdbc,
                    "ventas",
                    "fk_ventas_usuario",
                    "ALTER TABLE ventas ADD CONSTRAINT fk_ventas_usuario "
                            + "FOREIGN KEY (id_usuario) REFERENCES usuarios (id_usuario)");
            for (String method : new String[] { "Efectivo", "Tarjeta", "Yape", "Transferencia" }) {
                jdbc.update("INSERT IGNORE INTO formas_pago (nombre) VALUES (?)", method);
            }
            jdbc.update("INSERT IGNORE INTO categorias (nombre) SELECT DISTINCT categoria FROM productos");
            for (String category : new String[] { "Materiales", "Herramientas", "Maquetas", "Libros" }) {
                jdbc.update("INSERT IGNORE INTO categorias (nombre) VALUES (?)", category);
            }
            if (!seedDemoData) return;

            seedUser(jdbc, passwordEncoder, "00000001", "Administrador", "Demo", adminUser, adminPassword, "administrador");
            jdbc.update("UPDATE usuarios SET usuario = ?, nombres = 'Vendedor' "
                    + "WHERE dni = '00000002' AND usuario = 'cliente' "
                    + "AND NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM usuarios WHERE usuario = ?) u)", clientUser, clientUser);
            seedUser(jdbc, passwordEncoder, "00000002", "Vendedor", "Demo", clientUser, clientPassword, "cliente");
            seedProducts(jdbc);
        };
    }

    private void addColumnIfMissing(JdbcTemplate jdbc, String table, String column, String definition) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                table,
                column);
        if (count != null && count == 0) {
            jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private void addForeignKeyIfMissing(JdbcTemplate jdbc, String table, String constraint, String statement) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                        + "WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = ? "
                        + "AND CONSTRAINT_NAME = ? AND CONSTRAINT_TYPE = 'FOREIGN KEY'",
                Integer.class,
                table,
                constraint);
        if (count != null && count == 0) {
            jdbc.execute(statement);
        }
    }

    private void seedUser(
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            String dni,
            String names,
            String surnames,
            String username,
            String password,
            String profile) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM usuarios WHERE usuario = ? OR dni = ?", Integer.class, username, dni);
        if (exists == null || exists == 0) {
            jdbc.update(
                    "INSERT INTO usuarios (dni, nombres, apellidos, usuario, clave_hash, perfil, estado) "
                            + "VALUES (?, ?, ?, ?, ?, ?, 'activo')",
                    dni, names, surnames, username, passwordEncoder.encode(password), profile);
        }
    }

    private void seedProducts(JdbcTemplate jdbc) {
        Integer productCount = jdbc.queryForObject("SELECT COUNT(*) FROM productos", Integer.class);
        if (productCount == null || productCount != 0) return;

        Object[][] products = {
                { "Pintura acrílica premium", "Materiales", "Pintura acrílica para proyectos artísticos.", 58.00, 84 },
                { "Escuadra metálica T-30", "Herramientas", "Escuadra metálica de 30 cm.", 95.00, 23 },
                { "Maqueta de madera MDF", "Maquetas", "Madera MDF para elaboración de maquetas.", 42.50, 116 },
                { "Libro de planos arquitectónicos", "Libros", "Libro de referencia para dibujo arquitectónico.", 120.00, 17 }
        };
        for (Object[] product : products) {
            jdbc.update(
                    "INSERT INTO productos (nombre, categoria, descripcion, precio, cantidad) VALUES (?, ?, ?, ?, ?)",
                    product);
        }
    }
}
