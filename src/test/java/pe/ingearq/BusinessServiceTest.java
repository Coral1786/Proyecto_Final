package pe.ingearq;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

class BusinessServiceTest {
    private static final String ACTIVE_PRODUCT_QUERY = "SELECT id_producto, nombre, precio, cantidad FROM productos WHERE id_producto = ? AND activo = 1 FOR UPDATE";
    private static final String BALANCE_QUERY = "SELECT total_caja FROM caja ORDER BY id_operacion DESC LIMIT 1 FOR UPDATE";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BusinessService service = new BusinessService(jdbc, new ObjectMapper());

    @Test
    void rejectsSaleWithoutItems() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.createSale(Map.of(), 1L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsProductWithMissingName() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.createProduct(Map.of("category", "Tools", "price", 12)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsNegativeExpenseBeforeDatabaseAccess() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.registerExpense(Map.of("description", "Supplies", "amount", -1)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsZeroExpenseBeforeDatabaseAccess() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.registerExpense(Map.of("description", "Supplies", "amount", 0)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(jdbc);
    }

        @Test
        void rejectsSaleWhenProductIsNoLongerAvailable() {
        doReturn(java.util.List.of()).when(jdbc).query(
            eq(ACTIVE_PRODUCT_QUERY), ArgumentMatchers.<RowMapper<Map<String, Object>>>any(), eq(9L));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.createSale(Map.of("items", java.util.List.of(
                Map.of("productId", 9, "quantity", 1))), 1L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(jdbc).query(
            eq(ACTIVE_PRODUCT_QUERY), ArgumentMatchers.<RowMapper<Map<String, Object>>>any(), eq(9L));
        }

        @Test
        void reportsMissingProductWhenDeactivationChangesNoRows() {
        org.mockito.Mockito.when(jdbc.update(
            eq("UPDATE productos SET activo = 0 WHERE id_producto = ?"), eq(9L))).thenReturn(0);
        org.mockito.Mockito.when(jdbc.queryForObject(
            eq("SELECT COUNT(*) FROM productos WHERE id_producto = ?"), eq(Integer.class), eq(9L)))
            .thenReturn(0);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.deactivateProduct(9L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        }

        @Test
        void recordsPositiveExpenseAgainstZeroOpeningBalance() {
        doReturn(java.util.List.of()).when(jdbc)
            .query(eq(BALANCE_QUERY), ArgumentMatchers.<RowMapper<BigDecimal>>any());

        service.registerExpense(Map.of("description", "Supplies", "amount", "12.50"));

        verify(jdbc).update(
            eq("INSERT INTO caja (descripcion, ingresos, egresos, ganancias_dia, total_caja) VALUES (?, 0, ?, ?, ?)"),
            eq("Supplies"), eq(new java.math.BigDecimal("12.50")),
            eq(new java.math.BigDecimal("-12.50")), eq(new java.math.BigDecimal("-12.50")));
        }

        @Test
        void createsSaleWithTaxAndUpdatesStockAndCashBalance() {
        String saleId = "123e4567-e89b-12d3-a456-426614174000";
        Map<String, Object> product = Map.of(
            "id", 7L,
            "name", "Tool",
            "price", new BigDecimal("10.00"),
            "stock", 5);
        doReturn(java.util.List.of(product)).when(jdbc).query(
                eq(ACTIVE_PRODUCT_QUERY), ArgumentMatchers.<RowMapper<Map<String, Object>>>any(), eq(7L));
        doReturn(java.util.List.of(new BigDecimal("20.00"))).when(jdbc)
                .query(eq(BALANCE_QUERY), ArgumentMatchers.<RowMapper<BigDecimal>>any());
        Map<String, Object> body = Map.of(
            "id", saleId,
            "items", java.util.List.of(Map.of("productId", 7, "quantity", 2)),
            "type", "Factura",
            "customer", "Customer",
            "amountReceived", "25.00",
            "date", "2026-01-02");

        Map<String, Object> result = service.createSale(body, 1L);

        assertEquals(saleId, result.get("id"));
        assertEquals("Factura", result.get("type"));
        assertEquals("2026-01-02", result.get("date"));
        assertEquals(new BigDecimal("23.60"), result.get("total"));
        assertEquals(0, new BigDecimal("25.00").compareTo(new BigDecimal(result.get("amountReceived").toString())));
        assertEquals(0, new BigDecimal("1.40").compareTo(new BigDecimal(result.get("changeDue").toString())));
        verify(jdbc).update("UPDATE productos SET cantidad = cantidad - ? WHERE id_producto = ?", 2, 7L);
        verify(jdbc).update(
            eq("INSERT INTO ventas (id_comprobante, id_usuario, fecha_venta, productos, suma_total) VALUES (?, ?, ?, ?, ?)"),
            eq(saleId),
            eq(1L),
            eq(Timestamp.valueOf(LocalDateTime.of(2026, 1, 2, 12, 0))),
            ArgumentMatchers.anyString(),
            eq(new BigDecimal("23.60")));
        verify(jdbc).update(
            "INSERT INTO caja (id_comprobante, ingresos, egresos, ganancias_dia, total_caja) VALUES (?, ?, 0, ?, ?)",
            saleId, new BigDecimal("23.60"), new BigDecimal("23.60"), new BigDecimal("43.60"));
        }

        @Test
        void rejectsCashSaleWhenReceivedAmountDoesNotCoverTotal() {
        Map<String, Object> product = Map.of(
            "id", 7L,
            "name", "Tool",
            "price", new BigDecimal("10.00"),
            "stock", 5);
        doReturn(java.util.List.of(product)).when(jdbc).query(
                eq(ACTIVE_PRODUCT_QUERY), ArgumentMatchers.<RowMapper<Map<String, Object>>>any(), eq(7L));
        Map<String, Object> body = Map.of(
            "items", java.util.List.of(Map.of("productId", 7, "quantity", 2)),
            "amountReceived", "20.00");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.createSale(body, 1L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        }
}
