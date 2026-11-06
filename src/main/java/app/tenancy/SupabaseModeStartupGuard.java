package app.tenancy;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/**
 * The tenancy branch cannot run in 'supabase' mode unless the separate
 * verified-JWT security implementation (PR #31) has been integrated.
 *
 * Fail closed on configuration mistakes instead of silently serving the
 * tenant-scoped controller without a correctly configured security chain.
 */
@Component
@ConditionalOnProperty(name = "stockos.auth.mode", havingValue = "supabase")
public class SupabaseModeStartupGuard implements ApplicationRunner {
    private final ApplicationContext context;

    public SupabaseModeStartupGuard(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        ClassLoader loader = context.getClassLoader();
        if (!ClassUtils.isPresent("app.security.LinkedStockosUserFilter", loader)
                || !ClassUtils.isPresent("org.springframework.security.web.SecurityFilterChain", loader)) {
            throw new IllegalStateException(
                    "Modo Supabase bloqueado: integracao de JWT verificado ausente.");
        }

        try {
            Class<?> chainClass = ClassUtils.forName(
                    "org.springframework.security.web.SecurityFilterChain", loader);
            if (context.getBeanNamesForType(chainClass).length == 0) {
                throw new IllegalStateException(
                        "Modo Supabase bloqueado: nenhuma cadeia de seguranca JWT instalada.");
            }
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException(
                    "Modo Supabase bloqueado: Spring Security nao configurado.", exception);
        }
    }
}
