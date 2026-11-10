package repository;

import model.Usuario;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupabaseIdentityRepositoryContractTest {
    @Test
    void repositoryContractDoesNotAllowGlobalEnumerationFallback() {
        UsuarioRepository repository = mock(UsuarioRepository.class);
        UUID subject = UUID.randomUUID();
        when(repository.buscarPorSupabaseAuthUserId(subject)).thenReturn(Optional.empty());
        assertTrue(repository.buscarPorSupabaseAuthUserId(subject).isEmpty());
        verify(repository).buscarPorSupabaseAuthUserId(subject);
        verify(repository, never()).listarTodos();
    }

    @Test
    void jpaAdapterAlwaysUsesIndexedIdentityQuery() {
        JpaUsuarioRepository jpa = mock(JpaUsuarioRepository.class);
        UsuarioRepositoryAdapter adapter = new UsuarioRepositoryAdapter(jpa);
        UUID subject = UUID.randomUUID();
        Usuario linked = mock(Usuario.class);
        when(jpa.findBySupabaseAuthUserId(subject)).thenReturn(Optional.of(linked));
        assertSame(linked, adapter.buscarPorSupabaseAuthUserId(subject).orElseThrow());
        assertTrue(adapter.buscarPorSupabaseAuthUserId(null).isEmpty());
        verify(jpa).findBySupabaseAuthUserId(subject);
        verify(jpa, never()).findAllByOrderByRuAsc();
        verifyNoMoreInteractions(jpa);
    }
}
