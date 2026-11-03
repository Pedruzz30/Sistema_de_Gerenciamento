package integration;

import app.Main;

import app.tenancy.TenantMembershipGuard;
import app.tenancy.TenantMembershipGuard.Papel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = Main.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class TenantMembershipGuardIntegrationTest {
    private static final AtomicLong NEXT_CPF = new AtomicLong(84_000_000_000L);

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TenantMembershipGuard guard;

    @Test
    void somenteVinculoAtivoNaLojaCertaPermiteAcesso() {
        long ru = novoUsuario();
        UUID lojaA = novaLoja("Loja A");
        UUID lojaB = novaLoja("Loja B");
        vincular(lojaA, ru, 7, "OPERADOR");

        assertEquals(Papel.OPERADOR, guard.requireActiveMembership(lojaA, ru));
        assertThrows(SecurityException.class, () -> guard.requireActiveMembership(lojaB, ru));
        assertThrows(SecurityException.class, () -> guard.requireActiveMembership(null, ru));
        assertThrows(SecurityException.class, () -> guard.requireActiveMembership(lojaA, 0));
        assertThrows(SecurityException.class, () -> guard.requireAnyRole(lojaA, ru, Papel.PROPRIETARIO));
        guard.requireAnyRole(lojaA, ru, Papel.OPERADOR);

        jdbc.update("update usuarios_estabelecimentos set ativo = false where estabelecimento_id = ? and usuario_ru = ?", lojaA, ru);
        assertThrows(SecurityException.class, () -> guard.requireActiveMembership(lojaA, ru));
        jdbc.update("update usuarios_estabelecimentos set ativo = true where estabelecimento_id = ? and usuario_ru = ?", lojaA, ru);

        jdbc.update("update estabelecimentos set situacao = 'SUSPENSO' where id = ?", lojaA);
        assertThrows(SecurityException.class, () -> guard.requireActiveMembership(lojaA, ru));
        jdbc.update("update estabelecimentos set situacao = 'ATIVO' where id = ?", lojaA);

        jdbc.update("update usuarios set ativo = false where ru = ?", ru);
        assertThrows(SecurityException.class, () -> guard.requireActiveMembership(lojaA, ru));
    }

    @Test
    void descobertaMostraSomenteLojasAtivasDoUsuario() {
        long dono = novoUsuario();
        long outro = novoUsuario();
        UUID lojaA = novaLoja("Estabelecimento A");
        UUID lojaB = novaLoja("Estabelecimento B");
        UUID suspensa = novaLoja("Estabelecimento Suspenso");
        UUID revogada = novaLoja("Estabelecimento Revogado");
        vincular(lojaA, dono, 1, "PROPRIETARIO");
        vincular(lojaB, outro, 1, "OPERADOR");
        vincular(suspensa, dono, 2, "GERENTE");
        vincular(revogada, dono, 3, "OPERADOR");
        jdbc.update("update estabelecimentos set situacao = 'SUSPENSO' where id = ?", suspensa);
        jdbc.update("update usuarios_estabelecimentos set ativo = false where estabelecimento_id = ?", revogada);

        var visible = guard.listarEstabelecimentosAutorizados(dono);
        assertEquals(1, visible.size());
        assertEquals(lojaA, visible.get(0).id());
        assertEquals("Estabelecimento A", visible.get(0).nome());
        assertEquals(Papel.PROPRIETARIO, visible.get(0).papel());
        assertEquals(1, visible.get(0).ruLocal());

        assertEquals(lojaB, guard.listarEstabelecimentosAutorizados(outro).get(0).id());
        assertThrows(SecurityException.class, () -> guard.listarEstabelecimentosAutorizados(0));
        jdbc.update("update usuarios set ativo = false where ru = ?", dono);
        assertEquals(0, guard.listarEstabelecimentosAutorizados(dono).size());
    }

    @Test
    void ruLocalPodeSeRepetirEntreLojasMasNaoDentroDaMesmaLoja() {
        long ruA = novoUsuario();
        long ruB = novoUsuario();
        UUID lojaA = novaLoja("Bar do Norte");
        UUID lojaB = novaLoja("Cafe do Sul");
        vincular(lojaA, ruA, 1, "PROPRIETARIO");
        vincular(lojaB, ruB, 1, "OPERADOR");

        assertEquals(Papel.PROPRIETARIO, guard.requireActiveMembership(lojaA, ruA));
        assertEquals(Papel.OPERADOR, guard.requireActiveMembership(lojaB, ruB));
        assertThrows(SecurityException.class, () -> guard.requireActiveMembership(lojaA, ruB));
        assertThrows(DataIntegrityViolationException.class,
                () -> vincular(lojaA, ruB, 1, "OPERADOR"));
    }

    private UUID novaLoja(String nome) {
        return jdbc.queryForObject("insert into estabelecimentos (nome) values (?) returning id", UUID.class, nome);
    }

    private long novoUsuario() {
        // CPFs sintéticos distintos no banco efêmero, com transação revertida ao final.
        String cpf = Long.toString(NEXT_CPF.incrementAndGet());
        return jdbc.queryForObject("""
                insert into usuarios (nome, sobrenome, cpf, senha, ativo, perfil)
                values ('Teste', 'Isolamento', ?, 'nao-usar-em-producao', true, 'OPERADOR')
                returning ru
                """, Long.class, cpf);
    }

    private void vincular(UUID loja, long ru, int ruLocal, String papel) {
        jdbc.update("""
                insert into usuarios_estabelecimentos
                    (estabelecimento_id, usuario_ru, ru_local, papel)
                values (?, ?, ?, ?)
                """, loja, ru, ruLocal, papel);
    }
}
