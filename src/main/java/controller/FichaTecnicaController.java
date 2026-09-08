package controller;

import model.FichaTecnica;
import model.FichaTecnicaCustoSnapshot;
import model.FichaTecnicaIngrediente;
import model.FichaTecnicaIngredienteCustoSnapshot;
import model.FichaTecnicaVersao;
import model.Permissao;
import model.PoliticaFaltaIngrediente;
import model.Usuario;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import repository.FichaTecnicaCustoSnapshotRepository;
import repository.FichaTecnicaIngredienteCustoSnapshotRepository;
import repository.FichaTecnicaIngredienteRepository;
import repository.UsuarioRepository;
import service.CalculadoraCustoFichaTecnicaService;
import service.CustoFichaTecnicaResultado;
import service.FichaTecnicaIngredienteService;
import service.FichaTecnicaService;
import service.PublicacaoFichaTecnicaService;
import service.SimulacaoPrecoFichaTecnicaService;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api")
public class FichaTecnicaController {

    private static final Long FILIAL_PADRAO_ID = 1L;

    private final FichaTecnicaService fichaTecnicaService;
    private final FichaTecnicaIngredienteService ingredienteService;
    private final CalculadoraCustoFichaTecnicaService calculadoraCusto;
    private final PublicacaoFichaTecnicaService publicacaoService;
    private final SimulacaoPrecoFichaTecnicaService simulacaoService;
    private final FichaTecnicaIngredienteRepository ingredienteRepository;
    private final FichaTecnicaCustoSnapshotRepository snapshotRepository;
    private final FichaTecnicaIngredienteCustoSnapshotRepository ingredienteSnapshotRepository;
    private final UsuarioRepository usuarioRepository;

    public FichaTecnicaController(FichaTecnicaService fichaTecnicaService,
                                  FichaTecnicaIngredienteService ingredienteService,
                                  CalculadoraCustoFichaTecnicaService calculadoraCusto,
                                  PublicacaoFichaTecnicaService publicacaoService,
                                  SimulacaoPrecoFichaTecnicaService simulacaoService,
                                  FichaTecnicaIngredienteRepository ingredienteRepository,
                                  FichaTecnicaCustoSnapshotRepository snapshotRepository,
                                  FichaTecnicaIngredienteCustoSnapshotRepository ingredienteSnapshotRepository,
                                  UsuarioRepository usuarioRepository) {
        this.fichaTecnicaService = fichaTecnicaService;
        this.ingredienteService = ingredienteService;
        this.calculadoraCusto = calculadoraCusto;
        this.publicacaoService = publicacaoService;
        this.simulacaoService = simulacaoService;
        this.ingredienteRepository = ingredienteRepository;
        this.snapshotRepository = snapshotRepository;
        this.ingredienteSnapshotRepository = ingredienteSnapshotRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping("/cardapio/variacoes/{variacaoId}/ficha-tecnica")
    public ResponseEntity<VersaoResponse> criarFicha(
            @PathVariable long variacaoId,
            @RequestBody CriarFichaRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarEdicao(ruHeader);
        FichaTecnicaVersao versao = fichaTecnicaService.criarFicha(
                variacaoId,
                request.nome(),
                request.rendimentoQuantidade(),
                request.rendimentoUnidadeId(),
                request.quantidadePorPorcao(),
                request.tempoPreparoMinutos(),
                request.observacaoInterna(),
                usuario
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(versaoResponse(versao));
    }

    @GetMapping("/fichas-tecnicas/{fichaId}")
    public ResponseEntity<FichaResponse> buscarFicha(
            @PathVariable long fichaId,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        validarVisualizacao(ruHeader);
        return ResponseEntity.ok(FichaResponse.from(fichaTecnicaService.buscarFichaObrigatoria(fichaId)));
    }

    @GetMapping("/fichas-tecnicas/{fichaId}/versoes")
    public ResponseEntity<List<VersaoResponse>> listarVersoes(
            @PathVariable long fichaId,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        validarVisualizacao(ruHeader);
        return ResponseEntity.ok(fichaTecnicaService.listarVersoes(fichaId).stream()
                .map(this::versaoResponse)
                .toList());
    }

    @GetMapping("/fichas-tecnicas/{fichaId}/versoes/{versaoId}")
    public ResponseEntity<VersaoResponse> buscarVersao(
            @PathVariable long fichaId,
            @PathVariable long versaoId,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        validarVisualizacao(ruHeader);
        return ResponseEntity.ok(versaoResponse(fichaTecnicaService.buscarVersaoObrigatoria(fichaId, versaoId)));
    }

    @PutMapping("/fichas-tecnicas/{fichaId}/versoes/{versaoId}")
    public ResponseEntity<VersaoResponse> atualizarRascunho(
            @PathVariable long fichaId,
            @PathVariable long versaoId,
            @RequestBody AtualizarVersaoRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarEdicao(ruHeader);
        FichaTecnicaVersao versao = fichaTecnicaService.atualizarRascunho(
                fichaId,
                versaoId,
                request.rendimentoQuantidade(),
                request.rendimentoUnidadeId(),
                request.quantidadePorPorcao(),
                request.tempoPreparoMinutos(),
                request.observacaoInterna(),
                request.motivoAlteracao(),
                usuario
        );
        return ResponseEntity.ok(versaoResponse(versao));
    }

    @PostMapping("/fichas-tecnicas/{fichaId}/nova-versao")
    public ResponseEntity<VersaoResponse> novaVersao(
            @PathVariable long fichaId,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarEdicao(ruHeader);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(versaoResponse(fichaTecnicaService.novaVersao(fichaId, usuario)));
    }

    @PostMapping("/fichas-tecnicas/{fichaId}/versoes/{versaoId}/publicar")
    public ResponseEntity<VersaoResponse> publicar(
            @PathVariable long fichaId,
            @PathVariable long versaoId,
            @RequestBody PublicarFichaRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarPublicacao(ruHeader);
        Long filialId = request.filialReferenciaId() == null ? FILIAL_PADRAO_ID : request.filialReferenciaId();
        return ResponseEntity.ok(versaoResponse(publicacaoService.publicar(
                fichaId,
                versaoId,
                filialId,
                request.motivo(),
                usuario
        )));
    }

    @PostMapping("/fichas-tecnicas/{fichaId}/versoes/{versaoId}/descartar")
    public ResponseEntity<VersaoResponse> descartar(
            @PathVariable long fichaId,
            @PathVariable long versaoId,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarEdicao(ruHeader);
        return ResponseEntity.ok(versaoResponse(
                fichaTecnicaService.descartarRascunho(fichaId, versaoId, usuario)
        ));
    }

    @PostMapping("/fichas-tecnicas/{fichaId}/versoes/{versaoId}/ingredientes")
    public ResponseEntity<IngredienteResponse> adicionarIngrediente(
            @PathVariable long fichaId,
            @PathVariable long versaoId,
            @RequestBody IngredienteRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarEdicao(ruHeader);
        FichaTecnicaIngrediente ingrediente = ingredienteService.adicionar(
                fichaId,
                versaoId,
                request.produtoId(),
                request.unidadeInformadaId(),
                request.quantidadeInformada(),
                request.aplicarPerda(),
                request.percentualPerda(),
                request.ingredienteCritico(),
                request.politicaFalta(),
                request.ordem(),
                request.observacao(),
                usuario
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(IngredienteResponse.from(ingrediente));
    }

    @PutMapping("/fichas-tecnicas/{fichaId}/versoes/{versaoId}/ingredientes/{ingredienteId}")
    public ResponseEntity<IngredienteResponse> atualizarIngrediente(
            @PathVariable long fichaId,
            @PathVariable long versaoId,
            @PathVariable long ingredienteId,
            @RequestBody IngredienteRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarEdicao(ruHeader);
        return ResponseEntity.ok(IngredienteResponse.from(ingredienteService.atualizar(
                fichaId,
                versaoId,
                ingredienteId,
                request.produtoId(),
                request.unidadeInformadaId(),
                request.quantidadeInformada(),
                request.aplicarPerda(),
                request.percentualPerda(),
                request.ingredienteCritico(),
                request.politicaFalta(),
                request.ordem(),
                request.observacao(),
                usuario
        )));
    }

    @DeleteMapping("/fichas-tecnicas/{fichaId}/versoes/{versaoId}/ingredientes/{ingredienteId}")
    public ResponseEntity<Void> removerIngrediente(
            @PathVariable long fichaId,
            @PathVariable long versaoId,
            @PathVariable long ingredienteId,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = validarEdicao(ruHeader);
        ingredienteService.remover(fichaId, versaoId, ingredienteId, usuario);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/fichas-tecnicas/{fichaId}/versoes/{versaoId}/custo")
    public ResponseEntity<CustoResponse> calcularCusto(
            @PathVariable long fichaId,
            @PathVariable long versaoId,
            @RequestParam(value = "filialId", required = false) Long filialId,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        validarVerCusto(ruHeader);
        FichaTecnicaVersao versao = fichaTecnicaService.buscarVersaoObrigatoria(fichaId, versaoId);
        Long filial = filialId == null ? FILIAL_PADRAO_ID : filialId;
        CustoFichaTecnicaResultado custo = calculadoraCusto.calcular(
                versao,
                filial,
                versao.getFichaTecnica().getVariacaoItemCardapio().getPrecoVenda()
        );
        return ResponseEntity.ok(CustoResponse.from(custo));
    }

    @PostMapping("/fichas-tecnicas/{fichaId}/versoes/{versaoId}/simular-preco")
    public ResponseEntity<SimulacaoPrecoResponse> simularPreco(
            @PathVariable long fichaId,
            @PathVariable long versaoId,
            @RequestBody SimularPrecoRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        validarVerCusto(ruHeader);
        FichaTecnicaVersao versao = fichaTecnicaService.buscarVersaoObrigatoria(fichaId, versaoId);
        Long filial = request.filialId() == null ? FILIAL_PADRAO_ID : request.filialId();
        SimulacaoPrecoFichaTecnicaService.ResultadoSimulacao resultado = simulacaoService.simular(
                versao,
                filial,
                versao.getFichaTecnica().getVariacaoItemCardapio().getPrecoVenda(),
                request.cmvAlvoPercentual()
        );
        return ResponseEntity.ok(SimulacaoPrecoResponse.from(resultado));
    }

    private VersaoResponse versaoResponse(FichaTecnicaVersao versao) {
        List<IngredienteResponse> ingredientes = ingredienteRepository.listarPorVersao(versao.getId()).stream()
                .map(IngredienteResponse::from)
                .toList();
        List<CustoSnapshotResponse> snapshots = snapshotRepository.listarPorVersao(versao.getId()).stream()
                .map(this::snapshotResponse)
                .toList();
        return VersaoResponse.from(versao, ingredientes, snapshots);
    }

    private CustoSnapshotResponse snapshotResponse(FichaTecnicaCustoSnapshot snapshot) {
        return CustoSnapshotResponse.from(
                snapshot,
                ingredienteSnapshotRepository.listarPorSnapshot(snapshot.getId()).stream()
                        .map(IngredienteSnapshotResponse::from)
                        .toList()
        );
    }

    private Usuario validarVisualizacao(String ruHeader) {
        return ControllerUtils.resolveUserAndRequireAnyPermission(
                ruHeader,
                usuarioRepository,
                "Voce nao tem permissao para visualizar ficha tecnica.",
                Permissao.VER_ESTOQUE,
                Permissao.EDITAR_ESTOQUE,
                Permissao.VER_FINANCAS,
                Permissao.VER_VENDAS
        );
    }

    private Usuario validarEdicao(String ruHeader) {
        return ControllerUtils.resolveUserAndRequirePermission(
                ruHeader,
                usuarioRepository,
                Permissao.EDITAR_ESTOQUE,
                "Voce nao tem permissao para editar ficha tecnica."
        );
    }

    private Usuario validarPublicacao(String ruHeader) {
        return validarEdicao(ruHeader);
    }

    private Usuario validarVerCusto(String ruHeader) {
        return ControllerUtils.resolveUserAndRequireAnyPermission(
                ruHeader,
                usuarioRepository,
                "Voce nao tem permissao para visualizar custos de ficha tecnica.",
                Permissao.VER_FINANCAS,
                Permissao.EDITAR_ESTOQUE
        );
    }

    public record CriarFichaRequest(
            String nome,
            BigDecimal rendimentoQuantidade,
            Long rendimentoUnidadeId,
            BigDecimal quantidadePorPorcao,
            Integer tempoPreparoMinutos,
            String observacaoInterna
    ) {}

    public record AtualizarVersaoRequest(
            BigDecimal rendimentoQuantidade,
            Long rendimentoUnidadeId,
            BigDecimal quantidadePorPorcao,
            Integer tempoPreparoMinutos,
            String observacaoInterna,
            String motivoAlteracao
    ) {}

    public record PublicarFichaRequest(Long filialReferenciaId, String motivo) {}

    public record IngredienteRequest(
            Integer produtoId,
            Long unidadeInformadaId,
            BigDecimal quantidadeInformada,
            Boolean aplicarPerda,
            BigDecimal percentualPerda,
            Boolean ingredienteCritico,
            PoliticaFaltaIngrediente politicaFalta,
            Integer ordem,
            String observacao
    ) {}

    public record SimularPrecoRequest(Long filialId, BigDecimal cmvAlvoPercentual) {}

    public record FichaResponse(
            long id,
            long variacaoItemCardapioId,
            String nome,
            boolean ativa,
            Long versaoPublicadaAtualId,
            Integer numeroVersaoPublicadaAtual,
            String criadaEm,
            Long criadaPor,
            long version
    ) {
        public static FichaResponse from(FichaTecnica ficha) {
            FichaTecnicaVersao publicada = ficha.getVersaoPublicadaAtual();
            return new FichaResponse(
                    ficha.getId(),
                    ficha.getVariacaoItemCardapio().getId(),
                    ficha.getNome(),
                    ficha.isAtiva(),
                    publicada == null ? null : publicada.getId(),
                    publicada == null ? null : publicada.getNumeroVersao(),
                    ficha.getCriadaEm().toString(),
                    ficha.getCriadaPor(),
                    ficha.getVersion()
            );
        }
    }

    public record VersaoResponse(
            long id,
            long fichaTecnicaId,
            int numeroVersao,
            String status,
            BigDecimal rendimentoQuantidade,
            Long rendimentoUnidadeId,
            String rendimentoUnidadeCodigo,
            BigDecimal quantidadePorPorcao,
            Integer tempoPreparoMinutos,
            String observacaoInterna,
            String motivoAlteracao,
            String criadaEm,
            Long criadaPor,
            String publicadaEm,
            Long publicadaPor,
            String substituidaEm,
            long version,
            List<IngredienteResponse> ingredientes,
            List<CustoSnapshotResponse> snapshots
    ) {
        public static VersaoResponse from(FichaTecnicaVersao versao,
                                          List<IngredienteResponse> ingredientes,
                                          List<CustoSnapshotResponse> snapshots) {
            return new VersaoResponse(
                    versao.getId(),
                    versao.getFichaTecnica().getId(),
                    versao.getNumeroVersao(),
                    versao.getStatus().name(),
                    versao.getRendimentoQuantidade(),
                    versao.getRendimentoUnidade().getId(),
                    versao.getRendimentoUnidade().getCodigo(),
                    versao.getQuantidadePorPorcao(),
                    versao.getTempoPreparoMinutos(),
                    versao.getObservacaoInterna(),
                    versao.getMotivoAlteracao(),
                    versao.getCriadaEm().toString(),
                    versao.getCriadaPor(),
                    versao.getPublicadaEm() == null ? null : versao.getPublicadaEm().toString(),
                    versao.getPublicadaPor(),
                    versao.getSubstituidaEm() == null ? null : versao.getSubstituidaEm().toString(),
                    versao.getVersion(),
                    ingredientes,
                    snapshots
            );
        }
    }

    public record IngredienteResponse(
            long id,
            int produtoId,
            String produtoNome,
            Long unidadeInformadaId,
            String unidadeInformadaCodigo,
            BigDecimal quantidadeInformada,
            BigDecimal quantidadeLiquidaBase,
            BigDecimal percentualPerda,
            BigDecimal quantidadeBrutaBase,
            String unidadeBaseCodigo,
            boolean ingredienteCritico,
            String politicaFalta,
            int ordem,
            String observacao
    ) {
        public static IngredienteResponse from(FichaTecnicaIngrediente ingrediente) {
            return new IngredienteResponse(
                    ingrediente.getId(),
                    ingrediente.getProduto().getId(),
                    ingrediente.getProduto().getNome(),
                    ingrediente.getUnidadeInformada().getId(),
                    ingrediente.getUnidadeInformada().getCodigo(),
                    ingrediente.getQuantidadeInformada(),
                    ingrediente.getQuantidadeLiquidaBase(),
                    ingrediente.getPercentualPerda(),
                    ingrediente.getQuantidadeBrutaBase(),
                    ingrediente.getProduto().getUnidadeBase() == null ? null : ingrediente.getProduto().getUnidadeBase().getCodigo(),
                    ingrediente.isIngredienteCritico(),
                    ingrediente.getPoliticaFalta().name(),
                    ingrediente.getOrdem(),
                    ingrediente.getObservacao()
            );
        }
    }

    public record CustoResponse(
            long fichaTecnicaId,
            long versaoId,
            int numeroVersao,
            String status,
            Long filialId,
            BigDecimal rendimentoQuantidade,
            String rendimentoUnidade,
            List<IngredienteCustoResponse> ingredientes,
            List<CustoFichaTecnicaResultado.IngredientePendente> ingredientesPendentes,
            BigDecimal custoTotal,
            BigDecimal custoPorUnidadeRendimento,
            BigDecimal precoVenda,
            BigDecimal cmvPercentual,
            BigDecimal margemBrutaEstimada,
            BigDecimal margemBrutaPercentual,
            boolean custoCompleto,
            String tipoAnalise,
            String calculadoEm
    ) {
        public static CustoResponse from(CustoFichaTecnicaResultado resultado) {
            return new CustoResponse(
                    resultado.fichaTecnicaId(),
                    resultado.versaoId(),
                    resultado.numeroVersao(),
                    resultado.status().name(),
                    resultado.filialId(),
                    resultado.rendimentoQuantidade(),
                    resultado.rendimentoUnidadeCodigo(),
                    resultado.ingredientes().stream()
                            .map(IngredienteCustoResponse::from)
                            .toList(),
                    resultado.ingredientesPendentes(),
                    resultado.custoTotal(),
                    resultado.custoPorUnidadeRendimento(),
                    resultado.precoVenda(),
                    resultado.cmvPercentual(),
                    resultado.margemBrutaEstimada(),
                    resultado.margemBrutaPercentual(),
                    resultado.custoCompleto(),
                    resultado.tipoAnalise(),
                    resultado.calculadoEm().toString()
            );
        }
    }

    public record IngredienteCustoResponse(
            long ingredienteId,
            int produtoId,
            String produto,
            BigDecimal quantidadeBrutaBase,
            String unidadeBase,
            BigDecimal custoMedioUnitario,
            BigDecimal custoTotal,
            boolean custoDisponivel
    ) {
        public static IngredienteCustoResponse from(CustoFichaTecnicaResultado.IngredienteCusto ingrediente) {
            return new IngredienteCustoResponse(
                    ingrediente.ingredienteId(),
                    ingrediente.produtoId(),
                    ingrediente.produto(),
                    ingrediente.quantidadeBrutaBase(),
                    ingrediente.unidadeBaseCodigo(),
                    ingrediente.custoMedioUnitario(),
                    ingrediente.custoTotal(),
                    ingrediente.custoDisponivel()
            );
        }
    }

    public record CustoSnapshotResponse(
            long id,
            long versaoId,
            Long filialId,
            BigDecimal custoTotal,
            BigDecimal custoPorUnidadeRendimento,
            String calculadoEm,
            List<IngredienteSnapshotResponse> ingredientes
    ) {
        public static CustoSnapshotResponse from(FichaTecnicaCustoSnapshot snapshot,
                                                 List<IngredienteSnapshotResponse> ingredientes) {
            return new CustoSnapshotResponse(
                    snapshot.getId(),
                    snapshot.getFichaTecnicaVersao().getId(),
                    snapshot.getFilialId(),
                    snapshot.getCustoTotal(),
                    snapshot.getCustoPorUnidadeRendimento(),
                    snapshot.getCalculadoEm().toString(),
                    ingredientes
            );
        }
    }

    public record IngredienteSnapshotResponse(
            long id,
            long ingredienteId,
            int produtoId,
            String produto,
            BigDecimal quantidadeBase,
            BigDecimal custoMedioUnitario,
            BigDecimal custoTotalIngrediente
    ) {
        public static IngredienteSnapshotResponse from(FichaTecnicaIngredienteCustoSnapshot snapshot) {
            return new IngredienteSnapshotResponse(
                    snapshot.getId(),
                    snapshot.getFichaTecnicaIngrediente().getId(),
                    snapshot.getProduto().getId(),
                    snapshot.getProduto().getNome(),
                    snapshot.getQuantidadeBase(),
                    snapshot.getCustoMedioUnitario(),
                    snapshot.getCustoTotalIngrediente()
            );
        }
    }

    public record SimulacaoPrecoResponse(
            CustoResponse custo,
            BigDecimal precoSugeridoMatematico,
            List<BigDecimal> sugestoesComerciais,
            boolean simulacaoCompleta
    ) {
        public static SimulacaoPrecoResponse from(SimulacaoPrecoFichaTecnicaService.ResultadoSimulacao resultado) {
            return new SimulacaoPrecoResponse(
                    CustoResponse.from(resultado.custo()),
                    resultado.precoSugeridoMatematico(),
                    resultado.sugestoesComerciais(),
                    resultado.simulacaoCompleta()
            );
        }
    }
}
