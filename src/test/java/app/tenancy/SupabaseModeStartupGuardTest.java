package app.tenancy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class SupabaseModeStartupGuardTest {
    @Test
    void semFiltroDeJwtVerificadoNaoPermiteModoSupabase() {
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getClassLoader()).thenReturn(
                SupabaseModeStartupGuardTest.class.getClassLoader());

        SupabaseModeStartupGuard guard = new SupabaseModeStartupGuard(context);
        assertThrows(IllegalStateException.class,
                () -> guard.run(mock(ApplicationArguments.class)));
    }
}
