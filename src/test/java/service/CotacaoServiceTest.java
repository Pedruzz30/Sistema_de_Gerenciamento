package service;

import model.ClasseFuncionario;
import model.CotacaoMensal;
import model.Permissao;
import model.Produto;
import model.ResultadoComparacao;
import model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import repository.InMemoryCotacaoRepository;
import repository.InMemoryLogRepository;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CotacaoServiceTest {

    private CotacaoService service;
    private Usuario gerenteFinanceiro;
    private Produto produto;

    @BeforeEach
    void setUp() {
        service = new CotacaoService(new InMemoryCotacaoRepository(), new InMemoryLogRepository());

        ClasseFuncionario classe = new ClasseFuncionario(1, "GERENTE_FINANCEIRO", "Acompanha custos");
        classe.adicionarPermissao(Permissao.VER_FINANCAS);
        gerenteFinanceiro = new Usuario(1, "Maria", "Gerente", "111.444.777-35", "1234", classe, Usuario.Perfil.OPERADOR);

        produto = new Produto(10, "Arroz", 0, 5, BigDecimal.valueOf(25.90));
    }

    @Test
    void registrarCotacao_comMesmoProdutoEMes_substituiRegistroAnterior() {
        CotacaoMensal primeira = service.registrarCotacao(
                gerenteFinanceiro,
                produto,
                YearMonth.of(2026, 3),
                BigDecimal.valueOf(25.90),
                20
        );
        CotacaoMensal atualizada = service.registrarCotacao(
                gerenteFinanceiro,
                produto,
                YearMonth.of(2026, 3),
                BigDecimal.valueOf(27.10),
                30
        );

        assertEquals(primeira.getId(), atualizada.getId());
        assertEquals(1, service.buscarHistorico(produto).size());
        assertEquals(0, BigDecimal.valueOf(27.10).compareTo(service.buscarHistorico(produto).get(0).getPrecoUnitario()));
    }

    @Test
    void compararMeses_retornaVariacaoQuandoExistemDoisMesesConsecutivos() {
        service.registrarCotacao(gerenteFinanceiro, produto, YearMonth.of(2026, 2), BigDecimal.valueOf(20.00), 10);
        service.registrarCotacao(gerenteFinanceiro, produto, YearMonth.of(2026, 3), BigDecimal.valueOf(25.00), 10);

        Optional<ResultadoComparacao> resultado = service.compararComMesAnterior(produto, YearMonth.of(2026, 3));

        assertTrue(resultado.isPresent());
        assertEquals(0, BigDecimal.valueOf(25.00).subtract(BigDecimal.valueOf(20.00))
                .compareTo(resultado.get().getVariacaoAbsoluta()));
    }
}
