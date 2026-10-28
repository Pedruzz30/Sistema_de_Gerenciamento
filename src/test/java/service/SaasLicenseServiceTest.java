package service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SaasLicenseServiceTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final SaasLicenseService service=new SaasLicenseService(jdbc);

    @Test
    void activeEntitlementIsAccepted() {
        UUID company=UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(company))).thenReturn(true);
        assertDoesNotThrow(()->service.requireActive(company));
    }

    @Test
    void missingOrExpiredEntitlementDeniesSale() {
        UUID company=UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(company))).thenReturn(false);
        ResponseStatusException error=assertThrows(ResponseStatusException.class,
                ()->service.requireActive(company));
        assertEquals(HttpStatus.FORBIDDEN,error.getStatusCode());
    }

    @Test
    void missingEntitlementCanBeViewedReadOnly() {
        UUID company=UUID.randomUUID();
        when(jdbc.queryForList(anyString(),eq(company))).thenReturn(List.of());
        Map<String,Object> info=service.summary(company);
        assertEquals("SEM_LICENCA",info.get("status"));
        assertEquals(false,info.get("acesso_liberado"));
    }

    @Test
    void snapshotIsReturnedFromDbNotUserInput() {
        UUID company=UUID.randomUUID();
        when(jdbc.queryForList(anyString(),eq(company)))
             .thenReturn(List.of(Map.of("status","ATIVA","acesso_liberado",true)));
        assertEquals(true,service.summary(company).get("acesso_liberado"));
    }
}
