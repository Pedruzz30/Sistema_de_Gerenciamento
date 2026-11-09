package controller;

import model.CodigoVenda;
import model.LogAcao;
import model.Permissao;
import model.Usuario;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import repository.LogRepository;
import repository.UsuarioRepository;
import service.AutenticacaoService;
import service.CodigoVendaService;
import service.ProdutoRevendaService;

/**
 * Cadastro feito do próprio caixa, quando um código bipado não existe: produto novo ou
 * código novo para um produto que já existe. Quem não pode editar o estoque precisa do
 * login de quem pode, na hora, como na sangria. Por isso estas rotas valem também para
 * quem entrou por PIN.
 */
@RestController
@RequestMapping("/api/pdv/cadastro")
public class PdvCadastroController {

    private final ProdutoRevendaService produtoRevendaService;
    private final CodigoVendaService codigoVendaService;
    private final AutenticacaoService autenticacaoService;
    private final UsuarioRepository usuarioRepository;
    private final LogRepository logRepository;

    public PdvCadastroController(ProdutoRevendaService produtoRevendaService,
                                 CodigoVendaService codigoVendaService,
                                 AutenticacaoService autenticacaoService,
                                 UsuarioRepository usuarioRepository,
                                 LogRepository logRepository) {
        this.produtoRevendaService = produtoRevendaService;
        this.codigoVendaService = codigoVendaService;
        this.autenticacaoService = autenticacaoService;
        this.usuarioRepository = usuarioRepository;
        this.logRepository = logRepository;
    }

    @PostMapping("/produto")
    public ResponseEntity<ProdutoRevendaController.RevendaResponse> cadastrarProduto(
            @RequestBody ProdutoNovoRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario operador = operadorDeCaixa(ruHeader);
        if (request == null || request.produto() == null) {
            throw new IllegalArgumentException("Dados do produto sao obrigatorios.");
        }
        Usuario responsavel = responsavel(operador, resolverAutorizador(request.autorizacao()));
        ProdutoRevendaService.Resultado resultado = produtoRevendaService.cadastrar(responsavel, request.produto().paraEntrada());
        registrar(responsavel, operador, "PRODUTO_REVENDA_CADASTRADO",
                "Cadastro no caixa: " + resultado.produto().getNome() + " (ID: " + resultado.produto().getId() + ")");
        return ResponseEntity.ok(ProdutoRevendaController.RevendaResponse.from(resultado));
    }

    @PostMapping("/codigo")
    public ResponseEntity<ProdutoRevendaController.CodigoResponse> vincularCodigo(
            @RequestBody VincularCodigoRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario operador = operadorDeCaixa(ruHeader);
        if (request == null || request.codigo() == null || request.variacaoId() == null) {
            throw new IllegalArgumentException("Informe o codigo e o produto.");
        }
        Usuario responsavel = responsavel(operador, resolverAutorizador(request.autorizacao()));
        CodigoVenda codigo = codigoVendaService.cadastrar(responsavel, request.codigo(), request.variacaoId(), null);
        registrar(responsavel, operador, "CODIGO_VENDA_CADASTRADO",
                "Codigo " + codigo.getCodigo() + " ligado a " + codigo.getVariacao().getItemCardapio().getNome()
                        + " " + codigo.getVariacao().getNome() + " no caixa");
        return ResponseEntity.ok(ProdutoRevendaController.CodigoResponse.from(codigo));
    }

    private Usuario operadorDeCaixa(String ruHeader) {
        return ControllerUtils.resolveUserAndRequireAnyPermission(
                ruHeader, usuarioRepository, "Voce nao tem permissao para cadastrar no caixa.",
                Permissao.VER_VENDAS, Permissao.EDITAR_ESTOQUE);
    }

    static Usuario responsavel(Usuario operador, Usuario autorizador) {
        if (podeCadastrar(operador)) {
            return operador;
        }
        if (autorizador != null && autorizador.isAtivo() && podeCadastrar(autorizador)) {
            return autorizador;
        }
        throw new SecurityException(autorizador == null
                ? "Para cadastrar, o gerente precisa autorizar com a senha dele."
                : "Quem autorizou nao pode cadastrar produtos.");
    }

    private static boolean podeCadastrar(Usuario usuario) {
        return usuario != null && usuario.getClasse() != null
                && usuario.getClasse().possuiPermissao(Permissao.EDITAR_ESTOQUE);
    }

    private Usuario resolverAutorizador(CaixaController.AutorizacaoRequest autorizacao) {
        if (autorizacao == null || autorizacao.nome() == null || autorizacao.nome().isBlank()
                || autorizacao.senha() == null || autorizacao.senha().isBlank()) {
            return null;
        }
        return autenticacaoService.conferirCredenciais(
                        autorizacao.nome().trim(),
                        autorizacao.sobrenome() == null ? "" : autorizacao.sobrenome().trim(),
                        autorizacao.senha())
                .orElseThrow(() -> new SecurityException("Login do gerente invalido."));
    }

    private void registrar(Usuario responsavel, Usuario operador, String acao, String descricao) {
        String autorizado = responsavel != operador ? " de " + operador.getNomeCompleto() + ", autorizado por " + responsavel.getNomeCompleto() : "";
        logRepository.salvar(new LogAcao(0, responsavel.getRu(), acao, descricao + autorizado + "."));
    }

    public record ProdutoNovoRequest(ProdutoRevendaController.CadastroRevendaRequest produto,
                                     CaixaController.AutorizacaoRequest autorizacao) {}

    public record VincularCodigoRequest(String codigo, Long variacaoId,
                                        CaixaController.AutorizacaoRequest autorizacao) {}
}
