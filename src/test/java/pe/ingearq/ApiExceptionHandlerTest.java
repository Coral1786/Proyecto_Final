package pe.ingearq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void preservesStatusAndReasonForStatusExceptions() {
        var response = handler.handleStatus(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid request", response.getBody().get("error"));
    }

    @Test
    void returnsBadRequestForUnreadableJson() {
        var response = handler.handleInvalidJson();

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("El contenido enviado no es válido.", response.getBody().get("error"));
    }

    @Test
    void hidesUnexpectedExceptionDetails() {
        var response = handler.handleUnexpected(new IllegalStateException("sensitive detail"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Error interno del servidor. Revisa los datos e inténtalo de nuevo.", response.getBody().get("error"));
    }
}
