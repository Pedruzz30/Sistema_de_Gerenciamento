package controller;

import model.Usuario;
import repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods shared across REST controllers.
 */
public class ControllerUtils {
    private static final Logger log = LoggerFactory.getLogger(ControllerUtils.class);

    /**
     * Resolves the acting user from the X-User-RU request header.
     *
     * If the header is present and matches a known user, that user is returned.
     * Otherwise falls back to the supplied adminSuperior bean so that
     * existing behaviour is preserved for callers that do not send the header.
     */
    public static Usuario resolveUser(String ruHeader,
                                      UsuarioRepository repo,
                                      Usuario fallback) {
        if (ruHeader != null && !ruHeader.isBlank()) {
            try {
                long ru = Long.parseLong(ruHeader.trim());
                return repo.buscarPorRu(ru).orElseGet(() -> {
                    log.warn("X-User-RU '{}' não encontrado. Aplicando fallback para RU {}.",
                            ruHeader, fallback.getRu());
                    return fallback;
                });
            } catch (NumberFormatException ignored) {
                log.warn("X-User-RU inválido ('{}'). Aplicando fallback para RU {}.",
                        ruHeader, fallback.getRu());
            }
        } else {
            log.warn("X-User-RU ausente. Aplicando fallback para RU {}.", fallback.getRu());
        }
        return fallback;
    }
}
