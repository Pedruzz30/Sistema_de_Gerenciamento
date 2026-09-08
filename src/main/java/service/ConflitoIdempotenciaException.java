package service;

public class ConflitoIdempotenciaException extends ErroNegocioException {

    public ConflitoIdempotenciaException(String message) {
        super("CHAVE_IDEMPOTENCIA_REUTILIZADA_COM_PAYLOAD_DIFERENTE", message);
    }
}
