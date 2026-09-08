package controller;

import model.Permissao;
import model.PoliticaArredondamentoComposicaoMultipla;
import model.PoliticaPrecoComposicaoMultipla;
import model.Produto;
import model.TipoComposicaoVariacao;
import model.Usuario;
import model.VariacaoItemCardapio;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import repository.UsuarioRepository;
import service.ComposicaoMultiplaService;
import service.VariacaoItemCardapioService;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/cardapio")
public class CardapioVariacaoController {

    private final VariacaoItemCardapioService variacaoService;
    private final ComposicaoMultiplaService composicaoMultiplaService;
    private final UsuarioRepository usuarioRepository;

    public CardapioVariacaoController(VariacaoItemCardapioService variacaoService,
                                      ComposicaoMultiplaService composicaoMultiplaService,
                                      UsuarioRepository usuarioRepository) {
        this.variacaoService = variacaoService;
        this.composicaoMultiplaService = composicaoMultiplaService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/itens/{itemId}/variacoes")
    public ResponseEntity<List<VariacaoResponse>> listar(
            @PathVariable int itemId,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarVisualizacao(ruHeader);
        return ResponseEntity.ok(variacaoService.listarPorItem(itemId, usuario).stream()
                .map(VariacaoResponse::from)
                .toList());
    }

    @PostMapping("/itens/{itemId}/variacoes")
    public ResponseEntity<VariacaoResponse> criar(
            @PathVariable int itemId,
            @RequestBody VariacaoRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarEdicao(ruHeader);
        VariacaoItemCardapio variacao = variacaoService.criar(
                itemId,
                request.nome(),
                request.codigo(),
                request.precoVenda(),
                request.tipoComposicao(),
                request.produtoDiretoId(),
                request.quantidadeProdutoDiretoBase(),
                request.ativa(),
                request.padrao(),
                request.ordemExibicao(),
                usuario
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(VariacaoResponse.from(variacao));
    }

    @GetMapping("/variacoes/{variacaoId}")
    public ResponseEntity<VariacaoResponse> buscar(
            @PathVariable long variacaoId,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        validarVisualizacao(ruHeader);
        return ResponseEntity.ok(VariacaoResponse.from(variacaoService.buscarObrigatoria(variacaoId)));
    }

    @PutMapping("/variacoes/{variacaoId}")
    public ResponseEntity<VariacaoResponse> atualizar(
            @PathVariable long variacaoId,
            @RequestBody VariacaoRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarEdicao(ruHeader);
        VariacaoItemCardapio variacao = variacaoService.atualizar(
                variacaoId,
                request.nome(),
                request.codigo(),
                request.precoVenda(),
                request.tipoComposicao(),
                request.produtoDiretoId(),
                request.quantidadeProdutoDiretoBase(),
                request.ativa(),
                request.padrao(),
                request.ordemExibicao(),
                usuario
        );
        return ResponseEntity.ok(VariacaoResponse.from(variacao));
    }

    @PatchMapping("/variacoes/{variacaoId}/status")
    public ResponseEntity<VariacaoResponse> alterarStatus(
            @PathVariable long variacaoId,
            @RequestBody StatusVariacaoRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarEdicao(ruHeader);
        return ResponseEntity.ok(VariacaoResponse.from(
                variacaoService.alterarStatus(variacaoId, request.ativa(), usuario)
        ));
    }

    @PostMapping("/variacoes/backfill-padrao")
    public ResponseEntity<BackfillVariacaoResponse> backfillPadrao(
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarEdicao(ruHeader);
        VariacaoItemCardapioService.BackfillVariacaoResultado resultado =
                variacaoService.garantirVariacoesPadrao(usuario);
        return ResponseEntity.ok(new BackfillVariacaoResponse(resultado.criadas(), resultado.pendencias()));
    }

    @GetMapping("/variacoes/{variacaoId}/composicao-multipla")
    public ResponseEntity<ComposicaoMultiplaResponse> buscarComposicaoMultipla(
            @PathVariable long variacaoId,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        validarVisualizacao(ruHeader);
        ComposicaoMultiplaService.ConfiguracaoDetalhada detalhada =
                composicaoMultiplaService.buscarDetalhada(variacaoId);
        return ResponseEntity.ok(ComposicaoMultiplaResponse.from(detalhada));
    }

    @PutMapping("/variacoes/{variacaoId}/composicao-multipla")
    public ResponseEntity<ComposicaoMultiplaResponse> salvarComposicaoMultipla(
            @PathVariable long variacaoId,
            @RequestBody ComposicaoMultiplaRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        validarEdicao(ruHeader);
        composicaoMultiplaService.salvarConfiguracao(
                variacaoId,
                new ComposicaoMultiplaService.ConfiguracaoInput(
                        request.ativo(),
                        request.minimoPartes(),
                        request.maximoPartes(),
                        request.quantidadesPermitidas(),
                        request.permiteRepetirPartes(),
                        request.proporcoesIguaisObrigatorias(),
                        request.permiteProporcaoPersonalizada(),
                        request.politicaPreco(),
                        request.politicaArredondamento()
                )
        );
        return ResponseEntity.ok(ComposicaoMultiplaResponse.from(
                composicaoMultiplaService.buscarDetalhada(variacaoId)
        ));
    }

    @PutMapping("/variacoes/{variacaoId}/composicao-multipla/partes/{variacaoParteId}")
    public ResponseEntity<ComposicaoMultiplaResponse> salvarPartePermitida(
            @PathVariable long variacaoId,
            @PathVariable long variacaoParteId,
            @RequestBody PartePermitidaRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        validarEdicao(ruHeader);
        composicaoMultiplaService.salvarPartePermitida(
                variacaoId,
                variacaoParteId,
                new ComposicaoMultiplaService.PartePermitidaInput(
                        request.ativa(),
                        request.ordemExibicao()
                )
        );
        return ResponseEntity.ok(ComposicaoMultiplaResponse.from(
                composicaoMultiplaService.buscarDetalhada(variacaoId)
        ));
    }

    @PostMapping("/variacoes/{variacaoId}/composicao-multipla/previsao")
    public ResponseEntity<ComposicaoMultiplaPrevisaoResponse> preverComposicaoMultipla(
            @PathVariable long variacaoId,
            @RequestBody ComposicaoMultiplaPrevisaoRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        validarVisualizacao(ruHeader);
        ComposicaoMultiplaService.PreparacaoComposicaoMultipla previsao =
                composicaoMultiplaService.preparar(
                        variacaoService.buscarObrigatoria(variacaoId),
                        (request.partes() == null ? List.<PartePrevisaoRequest>of() : request.partes()).stream()
                                .map(parte -> new ComposicaoMultiplaService.ParteInput(
                                        parte.variacaoParteId(),
                                        parte.proporcao(),
                                        parte.configuracaoVersao(),
                                        List.of()
                                ))
                                .toList()
                );
        return ResponseEntity.ok(ComposicaoMultiplaPrevisaoResponse.from(previsao));
    }

    private Usuario validarVisualizacao(String ruHeader) {
        return ControllerUtils.resolveUserAndRequireAnyPermission(
                ruHeader,
                usuarioRepository,
                "Voce nao tem permissao para visualizar variacoes de cardapio.",
                Permissao.VER_VENDAS,
                Permissao.VER_ESTOQUE,
                Permissao.VER_FINANCAS
        );
    }

    private Usuario validarEdicao(String ruHeader) {
        return ControllerUtils.resolveUserAndRequirePermission(
                ruHeader,
                usuarioRepository,
                Permissao.EDITAR_ESTOQUE,
                "Voce nao tem permissao para editar variacoes de cardapio."
        );
    }

    public record VariacaoRequest(
            String nome,
            String codigo,
            BigDecimal precoVenda,
            TipoComposicaoVariacao tipoComposicao,
            Integer produtoDiretoId,
            BigDecimal quantidadeProdutoDiretoBase,
            Boolean ativa,
            Boolean padrao,
            Integer ordemExibicao
    ) {}

    public record StatusVariacaoRequest(boolean ativa) {}

    public record BackfillVariacaoResponse(int criadas, List<String> pendencias) {}

    public record ComposicaoMultiplaRequest(
            boolean ativo,
            int minimoPartes,
            int maximoPartes,
            String quantidadesPermitidas,
            boolean permiteRepetirPartes,
            boolean proporcoesIguaisObrigatorias,
            boolean permiteProporcaoPersonalizada,
            PoliticaPrecoComposicaoMultipla politicaPreco,
            PoliticaArredondamentoComposicaoMultipla politicaArredondamento
    ) {}

    public record PartePermitidaRequest(boolean ativa, int ordemExibicao) {}

    public record ComposicaoMultiplaPrevisaoRequest(List<PartePrevisaoRequest> partes) {}

    public record PartePrevisaoRequest(long variacaoParteId, BigDecimal proporcao, String configuracaoVersao) {}

    public record ComposicaoMultiplaPrevisaoResponse(
            BigDecimal precoUnitarioBase,
            String configuracaoHash,
            String resumoPartes,
            List<PartePrevisaoResponse> partes
    ) {
        public static ComposicaoMultiplaPrevisaoResponse from(ComposicaoMultiplaService.PreparacaoComposicaoMultipla previsao) {
            return new ComposicaoMultiplaPrevisaoResponse(
                    previsao.precoUnitarioBase(),
                    previsao.configuracaoHash(),
                    previsao.resumoPartes(),
                    previsao.partes().stream()
                            .map(parte -> new PartePrevisaoResponse(
                                    parte.variacaoParte().getId(),
                                    parte.variacaoParte().getNome(),
                                    parte.proporcao(),
                                    parte.variacaoParte().getPrecoVenda()
                            ))
                            .toList()
            );
        }
    }

    public record PartePrevisaoResponse(
            long variacaoParteId,
            String variacao,
            BigDecimal proporcao,
            BigDecimal precoReferencia
    ) {}

    public record ComposicaoMultiplaResponse(
            Long id,
            Long variacaoId,
            boolean ativo,
            Integer minimoPartes,
            Integer maximoPartes,
            String quantidadesPermitidas,
            Boolean permiteRepetirPartes,
            Boolean proporcoesIguaisObrigatorias,
            Boolean permiteProporcaoPersonalizada,
            PoliticaPrecoComposicaoMultipla politicaPreco,
            PoliticaArredondamentoComposicaoMultipla politicaArredondamento,
            List<PartePermitidaResponse> partesPermitidas
    ) {
        public static ComposicaoMultiplaResponse from(ComposicaoMultiplaService.ConfiguracaoDetalhada detalhada) {
            if (detalhada == null) {
                return new ComposicaoMultiplaResponse(
                        null, null, false, null, null, null,
                        null, null, null, null, null, List.of()
                );
            }
            return new ComposicaoMultiplaResponse(
                    detalhada.configuracao().getId(),
                    detalhada.configuracao().getVariacaoItemCardapio().getId(),
                    detalhada.configuracao().isAtivo(),
                    detalhada.configuracao().getMinimoPartes(),
                    detalhada.configuracao().getMaximoPartes(),
                    detalhada.configuracao().getQuantidadesPermitidas(),
                    detalhada.configuracao().isPermiteRepetirPartes(),
                    detalhada.configuracao().isProporcoesIguaisObrigatorias(),
                    detalhada.configuracao().isPermiteProporcaoPersonalizada(),
                    detalhada.configuracao().getPoliticaPreco(),
                    detalhada.configuracao().getPoliticaArredondamento(),
                    detalhada.partesPermitidas().stream().map(PartePermitidaResponse::from).toList()
            );
        }
    }

    public record PartePermitidaResponse(
            long id,
            long variacaoParteId,
            int itemParteId,
            String item,
            String variacao,
            String codigoVariacao,
            boolean ativa,
            int ordemExibicao
    ) {
        public static PartePermitidaResponse from(model.ConfiguracaoComposicaoMultiplaParte parte) {
            return new PartePermitidaResponse(
                    parte.getId(),
                    parte.getVariacaoParte().getId(),
                    parte.getVariacaoParte().getItemCardapio().getId(),
                    parte.getVariacaoParte().getItemCardapio().getNome(),
                    parte.getVariacaoParte().getNome(),
                    parte.getVariacaoParte().getCodigo(),
                    parte.isAtiva(),
                    parte.getOrdemExibicao()
            );
        }
    }

    public record VariacaoResponse(
            long id,
            int itemCardapioId,
            String itemCodigo,
            String itemNome,
            String nome,
            String codigo,
            BigDecimal precoVenda,
            TipoComposicaoVariacao tipoComposicao,
            Integer produtoDiretoId,
            String produtoDiretoNome,
            BigDecimal quantidadeProdutoDiretoBase,
            boolean ativa,
            boolean padrao,
            int ordemExibicao,
            long version
    ) {
        public static VariacaoResponse from(VariacaoItemCardapio variacao) {
            Produto produto = variacao.getProdutoDireto();
            return new VariacaoResponse(
                    variacao.getId(),
                    variacao.getItemCardapio().getId(),
                    variacao.getItemCardapio().getCodigo(),
                    variacao.getItemCardapio().getNome(),
                    variacao.getNome(),
                    variacao.getCodigo(),
                    variacao.getPrecoVenda(),
                    variacao.getTipoComposicao(),
                    produto == null ? null : produto.getId(),
                    produto == null ? null : produto.getNome(),
                    variacao.getQuantidadeProdutoDiretoBase(),
                    variacao.isAtiva(),
                    variacao.isPadrao(),
                    variacao.getOrdemExibicao(),
                    variacao.getVersion()
            );
        }
    }
}
