package service;

public class ConflitoIdempotenciaException extends ErroNegocioException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public ConflitoIdempotenciaException(String message) {
        super("CHAVE_IDEMPOTENCIA_REUTILIZADA_COM_PAYLOAD_DIFERENTE", message);
    }
}
