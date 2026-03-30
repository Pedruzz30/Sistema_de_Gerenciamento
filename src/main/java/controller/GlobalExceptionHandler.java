package controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralised error-response format for all REST controllers.
 *
 * Controllers may still catch exceptions themselves when they need
 * fine-grained 404 handling or other specialised behaviour. This handler
 * only fires for exceptions that escape those local catches.
 *
 * Response format: { "erro": "message" }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ErroResponse(String erro) {}

    /** Validation errors and invalid input → 400 Bad Request */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErroResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ErroResponse(ex.getMessage()));
    }

    /** Business rule violations → 400 Bad Request */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErroResponse> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.badRequest().body(new ErroResponse(ex.getMessage()));
    }

    /** Permission / security violations → 403 Forbidden */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErroResponse> handleSecurity(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErroResponse(ex.getMessage()));
    }

    /** Unexpected exceptions → 500 Internal Server Error */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErroResponse("Erro interno do servidor."));
    }
}
