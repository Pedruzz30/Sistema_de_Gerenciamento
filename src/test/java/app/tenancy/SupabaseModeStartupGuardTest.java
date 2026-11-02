package app.tenancy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class SupabaseModeStartupGuardTest {
    @Test
    void semCadeiaDeSegurancaJwtNaoPermiteModoSupabase() {
        ApplicationContext context = mock(ApplicationContext.class);
        when(context.getClassLoader()).thenReturn(
                SupabaseModeStartupGuardTest.class.getClassLoader());
        when(context.getBeanNamesForType(SecurityFilterChain.class))
                .thenReturn(new String[0]);

        SupabaseModeStartupGuard guard = new SupabaseModeStartupGuard(context);
        assertThrows(IllegalStateException.class,
                () -> guard.run(mock(ApplicationArguments.class)));
    }
}
