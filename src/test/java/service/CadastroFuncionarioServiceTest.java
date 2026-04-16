package service;

import model.ClasseFuncionario;
import model.Permissao;
import model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import repository.InMemoryLogRepository;
import repository.InMemoryUsuarioRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CadastroFuncionarioServiceTest {

    private CadastroFuncionarioService service;
    private Usuario superior;
    private ClasseFuncionario classeDestino;

    @BeforeEach
    void setUp() {
        service = new CadastroFuncionarioService(new InMemoryUsuarioRepository(), new InMemoryLogRepository());

        ClasseFuncionario classeSuperior = new ClasseFuncionario(1, "SUPERIOR", "Acesso total");
        classeSuperior.adicionarPermissao(Permissao.GERENCIAR_FUNCIONARIOS);
        superior = new Usuario(1, "Admin", "Superior", "529.982.247-25", "1234", classeSuperior, Usuario.Perfil.ADMIN);

        classeDestino = new ClasseFuncionario(2, "ESTOQUISTA", "Movimenta estoque");
        classeDestino.adicionarPermissao(Permissao.VER_ESTOQUE);
    }

    @Test
    void cadastrarFuncionario_comDadosValidos_persisteComRuGerado() {
        Usuario cadastrado = service.cadastrarFuncionario(
                superior,
                "Maria",
                "Silva",
                "111.444.777-35",
                "senha",
                classeDestino
        );

        assertTrue(cadastrado.getRu() > 0);
        assertEquals("Maria Silva", cadastrado.getNomeCompleto());
        assertTrue(cadastrado.isAtivo());
    }

    @Test
    void cadastrarFuncionario_comCpfDuplicado_lancaIllegalArgumentException() {
        service.cadastrarFuncionario(superior, "Maria", "Silva", "111.444.777-35", "senha", classeDestino);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.cadastrarFuncionario(superior, "Maria", "Souza", "11144477735", "outra", classeDestino)
        );
    }

    @Test
    void mudarClasseEDesativar_preservaEstadoDoFuncionario() {
        Usuario cadastrado = service.cadastrarFuncionario(
                superior,
                "Joao",
                "Costa",
                "935.411.347-80",
                "senha",
                classeDestino
        );

        ClasseFuncionario novaClasse = new ClasseFuncionario(3, "CAIXA", "Opera vendas");
        novaClasse.adicionarPermissao(Permissao.VER_VENDAS);

        Usuario promovido = service.mudarClasse(superior, cadastrado.getRu(), novaClasse);
        assertEquals("CAIXA", promovido.getClasse().getNome());

        service.desativarFuncionario(superior, cadastrado.getRu());
        assertFalse(promovido.isAtivo());
    }
}
