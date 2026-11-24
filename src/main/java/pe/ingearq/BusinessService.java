package pe.ingearq;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class BusinessService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public BusinessService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> getProducts() {
        return jdbc.query(
                "SELECT id_producto AS id, nombre AS name, categoria AS category, descripcion AS description, "
                        + "precio AS price, cantidad AS stock, activo AS active FROM productos ORDER BY nombre",
                (rs, row) -> {
                    Map<String, Object> product = new LinkedHashMap<>();
                    product.put("id", rs.getLong("id"));
                    product.put("name", rs.getString("name"));
                    product.put("category", rs.getString("category"));
                    product.put("description", rs.getString("description"));
                    product.put("price", rs.getBigDecimal("price"));
                    product.put("stock", rs.getInt("stock"));
                    product.put("active", rs.getBoolean("active"));
                    product.put("color", new String[] { "warm", "gold", "green", "slate" }[(int) (rs.getLong("id") % 4)]);
                    return product;
                });
    }

    public List<Map<String, Object>> getCategories() {
        return jdbc.query("SELECT id_categoria AS id, nombre AS name FROM categorias ORDER BY nombre", (rs, row) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("id"));
            item.put("name", rs.getString("name"));
            return item;
        });
    }

    public List<Map<String, Object>> getPaymentMethods() {
        return jdbc.query("SELECT id_forma_pago AS id, nombre AS name FROM formas_pago ORDER BY id_forma_pago", (rs, row) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("id"));
            item.put("name", rs.getString("name"));
            return item;
        });
    }

    private void requireCatalogValue(String table, String value, String label) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE nombre = ?", Integer.class, value);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona " + label + " de la lista.");
        }
    }

    public long createProduct(Map<String, Object> body) {
        String name = requiredText(body, "name", 150);
        String category = requiredText(body, "category", 100);
        String description = optionalText(body, "description", 500);
        BigDecimal price = nonNegativeDecimal(body.get("price"), "precio");
        int stock = nonNegativeInteger(body.getOrDefault("stock", 0), "cantidad");
        requireCatalogValue("categorias", category, "una categoría");
        jdbc.update(
                "INSERT INTO productos (nombre, categoria, descripcion, precio, cantidad, activo) VALUES (?, ?, ?, ?, ?, 1)",
                name, category, description, price, stock);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public void updateProduct(long id, Map<String, Object> body) {
        String name = requiredText(body, "name", 150);
        String category = requiredText(body, "category", 100);
        String description = optionalText(body, "description", 500);
        BigDecimal price = nonNegativeDecimal(body.get("price"), "precio");
        int stock = nonNegativeInteger(body.get("stock"), "cantidad");
        requireCatalogValue("categorias", category, "una categoría");
        boolean active = !Boolean.FALSE.equals(body.get("active"));
        int changed = jdbc.update(
                "UPDATE productos SET nombre = ?, categoria = ?, descripcion = ?, precio = ?, cantidad = ?, activo = ? WHERE id_producto = ?",
                name, category, description, price, stock, active, id);
        if (changed == 0 && !exists("productos", "id_producto", id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado.");
        }
    }

    public void deactivateProduct(long id) {
        int changed = jdbc.update("UPDATE productos SET activo = 0 WHERE id_producto = ?", id);
        if (changed == 0 && !exists("productos", "id_producto", id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado.");
        }
    }

    public List<Map<String, Object>> getSales() {
        return jdbc.query(
                "SELECT id_comprobante, fecha_venta, productos, suma_total FROM ventas ORDER BY fecha_venta DESC",
                (rs, row) -> mapSale(
                        rs.getString("id_comprobante"),
                        rs.getTimestamp("fecha_venta").toLocalDateTime(),
                        rs.getString("productos"),
                        rs.getBigDecimal("suma_total")));
    }

    @Transactional
    public Map<String, Object> createSale(Map<String, Object> body, long idTrabajador) {
        Object itemValue = body.get("items");
        if (!(itemValue instanceof List<?> submittedItems) || submittedItems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agrega al menos un producto a la venta.");
        }

        List<Map<String, Object>> saleItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Object submittedItem : submittedItems) {
            if (!(submittedItem instanceof Map<?, ?> item)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La venta contiene un producto inválido.");
            }
            long productId = positiveLong(item.get("productId"), "producto");
            int quantity = nonNegativeInteger(item.get("quantity"), "cantidad");
            if (quantity < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe ser mayor que cero.");

            List<Map<String, Object>> products = jdbc.query(
                    "SELECT id_producto, nombre, precio, cantidad FROM productos WHERE id_producto = ? AND activo = 1 FOR UPDATE",
                    (rs, row) -> {
                        Map<String, Object> product = new LinkedHashMap<>();
                        product.put("id", rs.getLong("id_producto"));
                        product.put("name", rs.getString("nombre"));
                        product.put("price", rs.getBigDecimal("precio"));
                        product.put("stock", rs.getInt("cantidad"));
                        return product;
                    },
                    productId);
            if (products.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uno de los productos ya no está disponible.");

            Map<String, Object> product = products.get(0);
            int currentStock = (Integer) product.get("stock");
            if (currentStock < quantity) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stock insuficiente para " + product.get("name") + ".");
            }

            BigDecimal unitPrice = (BigDecimal) product.get("price");
            BigDecimal itemSubtotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(itemSubtotal);
            Map<String, Object> savedItem = new LinkedHashMap<>();
            savedItem.put("productId", productId);
            savedItem.put("name", product.get("name"));
            savedItem.put("quantity", quantity);
            savedItem.put("unitPrice", unitPrice);
            savedItem.put("subtotal", itemSubtotal);
            saleItems.add(savedItem);
            jdbc.update("UPDATE productos SET cantidad = cantidad - ? WHERE id_producto = ?", quantity, productId);
        }

        BigDecimal total = subtotal.multiply(new BigDecimal("1.18")).setScale(2, RoundingMode.HALF_UP);
        String token = validUuid(body.get("id")) ? String.valueOf(body.get("id")) : UUID.randomUUID().toString();
        String type = "Factura".equals(body.get("type")) ? "Factura" : "Boleta";
        Map<String, Object> saleData = new LinkedHashMap<>();
        saleData.put("items", saleItems);
        saleData.put("type", type);
        saleData.put("customer", cleanText(body.get("customer"), "Cliente general", 200));
        saleData.put("documentNumber", cleanText(body.get("documentNumber"), "", 30));
        String paymentMethod = cleanText(body.get("paymentMethod"), "Efectivo", 50);
        saleData.put("paymentMethod", paymentMethod);
        if (body.get("paymentMethod") != null) {
            requireCatalogValue("formas_pago", paymentMethod, "una forma de pago");
        }
        BigDecimal amountReceived = "Efectivo".equalsIgnoreCase(paymentMethod)
                ? nonNegativeDecimal(body.get("amountReceived"), "monto recibido")
                : total;
        if (amountReceived.compareTo(total) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "El monto recibido no cubre el total de la venta.");
        }
        saleData.put("amountReceived", amountReceived);
        saleData.put("changeDue", amountReceived.subtract(total));
        saleData.put("status", "Emitido");

        LocalDateTime saleDate = parseDate(body.get("date"));
        jdbc.update(
        "INSERT INTO ventas (id_comprobante, id_usuario, fecha_venta, productos, suma_total) VALUES (?, ?, ?, ?, ?)",
        token, idTrabajador, Timestamp.valueOf(saleDate), writeJson(saleData), total);
        BigDecimal balance = currentBalanceForUpdate();
        jdbc.update(
                "INSERT INTO caja (id_comprobante, ingresos, egresos, ganancias_dia, total_caja) VALUES (?, ?, 0, ?, ?)",
                token, total, total, balance.add(total));

        return mapSale(token, saleDate, writeJson(saleData), total);
    }

    @Transactional
    public void updateSale(String id, Map<String, Object> body) {
        Map<String, Object> current = findSaleDataForUpdate(id);
        if ("Anulado".equals(current.get("status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede editar un comprobante anulado.");
        }
        current.put("type", "Factura".equals(body.get("type")) ? "Factura" : "Boleta");
        current.put("customer", cleanText(body.get("customer"), current.get("customer").toString(), 200));
        current.put("documentNumber", cleanText(body.get("documentNumber"), current.get("documentNumber").toString(), 30));
        current.put("paymentMethod", cleanText(body.get("paymentMethod"), current.get("paymentMethod").toString(), 50));
        requireCatalogValue("formas_pago", current.get("paymentMethod").toString(), "una forma de pago");
        jdbc.update("UPDATE ventas SET productos = ? WHERE id_comprobante = ?", writeJson(current), id);
    }

    @Transactional
    public void cancelSale(String id) {
        Map<String, Object> sale = findSaleDataForUpdate(id);
        if ("Anulado".equals(sale.get("status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El comprobante ya está anulado.");
        }
        Object itemsValue = sale.get("items");
        if (itemsValue instanceof List<?> items) {
            for (Object value : items) {
                if (value instanceof Map<?, ?> item && item.get("productId") != null) {
                    jdbc.update(
                            "UPDATE productos SET cantidad = cantidad + ? WHERE id_producto = ?",
                            nonNegativeInteger(item.get("quantity"), "cantidad"),
                            positiveLong(item.get("productId"), "producto"));
                }
            }
        }
        BigDecimal total = jdbc.queryForObject("SELECT suma_total FROM ventas WHERE id_comprobante = ?", BigDecimal.class, id);
        sale.put("status", "Anulado");
        jdbc.update("UPDATE ventas SET productos = ? WHERE id_comprobante = ?", writeJson(sale), id);
        BigDecimal balance = currentBalanceForUpdate();
        jdbc.update(
                "INSERT INTO caja (id_comprobante, descripcion, ingresos, egresos, ganancias_dia, total_caja) "
                        + "VALUES (?, ?, 0, ?, ?, ?)",
                id, "Anulación INV-" + id, total, total.negate(), balance.subtract(total));
    }

    @Transactional
    public void registerExpense(Map<String, Object> body) {
        String description = requiredText(body, "description", 255);
        BigDecimal amount = nonNegativeDecimal(body.get("amount"), "monto");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El monto debe ser mayor que cero.");
        }
        BigDecimal balance = currentBalanceForUpdate();
        jdbc.update(
                "INSERT INTO caja (descripcion, ingresos, egresos, ganancias_dia, total_caja) VALUES (?, 0, ?, ?, ?)",
                description, amount, amount.negate(), balance.subtract(amount));
    }

    public Map<String, Object> getCashSummary() {
        Map<String, Object> totals = jdbc.queryForMap(
                "SELECT COALESCE(SUM(ingresos), 0) AS income, COALESCE(SUM(egresos), 0) AS expenses, "
                        + "COALESCE(SUM(ganancias_dia), 0) AS net FROM caja WHERE DATE(fecha_operacion) = CURDATE()");
        List<BigDecimal> balances = jdbc.query(
                "SELECT total_caja FROM caja ORDER BY id_operacion DESC LIMIT 1",
                (rs, row) -> rs.getBigDecimal("total_caja"));
        Integer saleCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ventas WHERE COALESCE(JSON_UNQUOTE(JSON_EXTRACT(productos, '$.status')), 'Emitido') <> 'Anulado'",
                Integer.class);
        List<Map<String, Object>> operations = jdbc.query(
                "SELECT c.id_operacion, c.id_comprobante, c.fecha_operacion, c.descripcion, c.ingresos, c.egresos, v.productos "
                        + "FROM caja c LEFT JOIN ventas v ON v.id_comprobante = c.id_comprobante "
                        + "ORDER BY c.fecha_operacion DESC, c.id_operacion DESC LIMIT 100",
                (rs, row) -> mapCashOperation(rs));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("income", totals.get("income"));
        result.put("expenses", totals.get("expenses"));
        result.put("net", totals.get("net"));
        result.put("balance", balances.isEmpty() ? BigDecimal.ZERO : balances.get(0));
        result.put("saleCount", saleCount == null ? 0 : saleCount);
        result.put("operations", operations);
        return result;
    }

    private Map<String, Object> mapCashOperation(ResultSet rs) throws SQLException {
        String id = rs.getString("id_comprobante");
        BigDecimal income = rs.getBigDecimal("ingresos");
        BigDecimal expense = rs.getBigDecimal("egresos");
        boolean isIncome = income.compareTo(BigDecimal.ZERO) > 0;
        String description = rs.getString("descripcion");
        String saleJson = rs.getString("productos");
        Map<String, Object> saleData = saleJson == null ? Map.of() : readSaleData(saleJson);
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("id", rs.getLong("id_operacion"));
        operation.put("code", id == null ? null : "INV-" + id);
        operation.put("label", isIncome ? "Venta " + id : (description == null ? "Egreso de caja" : description));
        operation.put("date", rs.getTimestamp("fecha_operacion").toLocalDateTime().toString());
        operation.put("type", isIncome ? "ingreso" : "egreso");
        operation.put("amount", isIncome ? income : expense);
        operation.put("status", saleData.getOrDefault("status", "Emitido"));
        return operation;
    }

    private Map<String, Object> mapSale(String id, LocalDateTime date, String json, BigDecimal total) {
        Map<String, Object> data = readSaleData(json);
        Map<String, Object> sale = new LinkedHashMap<>();
        sale.put("id", id);
        sale.put("code", "INV-" + id);
        sale.put("type", data.getOrDefault("type", "Boleta"));
        sale.put("documentNumber", data.getOrDefault("documentNumber", ""));
        sale.put("customer", data.getOrDefault("customer", "Cliente general"));
        sale.put("paymentMethod", data.getOrDefault("paymentMethod", "Efectivo"));
        sale.put("date", date.toLocalDate().toString());
        sale.put("total", total);
        sale.put("amountReceived", data.getOrDefault("amountReceived", total));
        sale.put("changeDue", data.getOrDefault("changeDue", BigDecimal.ZERO));
        sale.put("items", data.getOrDefault("items", List.of()));
        sale.put("status", data.getOrDefault("status", "Emitido"));
        return sale;
    }

    private Map<String, Object> findSaleDataForUpdate(String id) {
        List<Map<String, Object>> rows = jdbc.query(
                "SELECT productos FROM ventas WHERE id_comprobante = ? FOR UPDATE",
                (rs, row) -> readSaleData(rs.getString("productos")),
                id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprobante no encontrado.");
        return rows.get(0);
    }

    private BigDecimal currentBalanceForUpdate() {
        List<BigDecimal> balances = jdbc.query(
                "SELECT total_caja FROM caja ORDER BY id_operacion DESC LIMIT 1 FOR UPDATE",
                (rs, row) -> rs.getBigDecimal("total_caja"));
        return balances.isEmpty() ? BigDecimal.ZERO : balances.get(0);
    }

    private Map<String, Object> readSaleData(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JacksonException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo leer el detalle de la venta.");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar el detalle de la venta.");
        }
    }

    private LocalDateTime parseDate(Object value) {
        if (value == null || value.toString().isBlank()) return LocalDateTime.now();
        try {
            return LocalDate.parse(value.toString()).atTime(12, 0);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de venta no es válida.");
        }
    }

    private boolean exists(String table, String key, long id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + key + " = ?", Integer.class, id);
        return count != null && count > 0;
    }

    private boolean validUuid(Object value) {
        try {
            UUID.fromString(String.valueOf(value));
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private long positiveLong(Object value, String label) {
        try {
            long parsed = Long.parseLong(String.valueOf(value));
            if (parsed > 0) return parsed;
        } catch (NumberFormatException ignored) {
            // Fall through to the validation response.
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El " + label + " no es válido.");
    }

    private int nonNegativeInteger(Object value, String label) {
        try {
            int parsed = Integer.parseInt(String.valueOf(value));
            if (parsed >= 0) return parsed;
        } catch (NumberFormatException ignored) {
            // Fall through to the validation response.
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La " + label + " no es válida.");
    }

    private BigDecimal nonNegativeDecimal(Object value, String label) {
        try {
            BigDecimal parsed = new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
            if (parsed.compareTo(BigDecimal.ZERO) >= 0) return parsed;
        } catch (NumberFormatException ignored) {
            // Fall through to the validation response.
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El " + label + " no es válido.");
    }

    private String requiredText(Map<String, Object> body, String key, int maxLength) {
        Object value = body.get(key);
        if (value == null || value.toString().trim().isEmpty() || value.toString().trim().length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El campo " + key + " es obligatorio o demasiado largo.");
        }
        return value.toString().trim();
    }

    private String optionalText(Map<String, Object> body, String key, int maxLength) {
        return cleanText(body.get(key), "", maxLength);
    }

    private String cleanText(Object value, String fallback, int maxLength) {
        if (value == null) return fallback;
        String text = value.toString().trim();
        if (text.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uno de los campos de texto es demasiado largo.");
        }
        return text.isEmpty() ? fallback : text;
    }
}
