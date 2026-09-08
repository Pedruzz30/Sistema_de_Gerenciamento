package service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ErroNegocioException extends RuntimeException {

    private final String codigo;
    private final Map<String, Object> detalhes;

    public ErroNegocioException(String codigo, String mensagem) {
        this(codigo, mensagem, Map.of());
    }

    public ErroNegocioException(String codigo, String mensagem, Map<String, Object> detalhes) {
        super(mensagem);
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("Codigo do erro de negocio e obrigatorio.");
        }
        this.codigo = codigo.trim();
        this.detalhes = detalhes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(detalhes));
    }

    public String getCodigo() {
        return codigo;
    }

    public Map<String, Object> getDetalhes() {
        return detalhes;
    }
}
