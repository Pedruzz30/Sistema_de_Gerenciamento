package controller;

import model.CategoriaEstoque;
import model.Permissao;
import model.Usuario;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import repository.UsuarioRepository;
import service.ImportacaoNfeService;
import service.NfeXmlParser;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Importação do XML da NF-e do fornecedor.
 *
 * <ul>
 *   <li>{@code POST /api/notas/importacao/previa}: lê o XML e sugere, sem gravar.</li>
 *   <li>{@code POST /api/notas/importacao}: grava com as decisões conferidas.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/notas/importacao")
public class ImportacaoNfeController {

    private final ImportacaoNfeService importacaoNfeService;
    private final UsuarioRepository usuarioRepository;

    public ImportacaoNfeController(ImportacaoNfeService importacaoNfeService, UsuarioRepository usuarioRepository) {
        this.importacaoNfeService = importacaoNfeService;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping("/previa")
    public ResponseEntity<PreviaResponse> previa(@RequestBody PreviaRequest request,
                                                 @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = exigirPermissao(ruHeader);
        return ResponseEntity.ok(PreviaResponse.from(
                importacaoNfeService.previa(usuario, request == null ? null : request.xml())));
    }

    @PostMapping
    public ResponseEntity<ImportacaoNfeService.Resultado> importar(
            @RequestBody ImportarRequest request,
            @RequestHeader(value = "X-User-RU", required = false) String ruHeader) {
        Usuario usuario = exigirPermissao(ruHeader);
        if (request == null) {
            throw new IllegalArgumentException("Envie o arquivo XML da nota.");
        }
        List<ImportacaoNfeService.Decisao> decisoes = request.itens() == null
                ? List.of()
                : request.itens().stream().map(DecisaoRequest::paraDecisao).toList();
        return ResponseEntity.ok(importacaoNfeService.importar(usuario, request.xml(), decisoes));
    }

    private Usuario exigirPermissao(String ruHeader) {
        return ControllerUtils.resolveUserAndRequirePermission(
                ruHeader, usuarioRepository, Permissao.EDITAR_ESTOQUE,
                "Voce nao tem permissao para importar notas.");
    }

    public record PreviaRequest(String xml) {}

    public record ImportarRequest(String xml, List<DecisaoRequest> itens) {}

    public record DecisaoRequest(
            Integer numeroItem,
            String acao,
            Integer produtoId,
            Integer unidadesPorEmbalagem,
            String nome,
            BigDecimal precoVenda,
            BigDecimal precoGelado,
            BigDecimal precoFardo,
            Integer quantidadeMinima,
            String categoriaCardapioCodigo,
            String categoriaCardapioNome,
            String categoriaEstoque
    ) {
        ImportacaoNfeService.Decisao paraDecisao() {
            if (numeroItem == null) {
                throw new IllegalArgumentException("Item da nota sem numero.");
            }
            return new ImportacaoNfeService.Decisao(
                    numeroItem,
                    parseAcao(acao),
                    produtoId,
                    unidadesPorEmbalagem == null ? 1 : unidadesPorEmbalagem,
                    nome,
                    precoVenda,
                    precoGelado,
                    precoFardo,
                    quantidadeMinima == null ? 0 : quantidadeMinima,
                    categoriaCardapioCodigo,
                    categoriaCardapioNome,
                    parseCategoria(categoriaEstoque)
            );
        }

        private static ImportacaoNfeService.Acao parseAcao(String valor) {
            try {
                return valor == null ? null : ImportacaoNfeService.Acao.valueOf(valor.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Acao invalida para o item da nota: " + valor);
            }
        }

        private static CategoriaEstoque parseCategoria(String valor) {
            if (valor == null || valor.isBlank()) {
                return null;
            }
            try {
                return CategoriaEstoque.valueOf(valor.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Categoria de estoque invalida: " + valor);
            }
        }
    }

    public record ItemPreviaResponse(
            int numero,
            String codigoFornecedor,
            String descricao,
            String ncm,
            String cest,
            String eanComercial,
            String eanTributavel,
            String unidadeComercial,
            BigDecimal quantidadeComercial,
            String unidadeTributavel,
            BigDecimal quantidadeTributavel,
            BigDecimal custoTotal,
            Integer produtoId,
            String produtoNome,
            String origem,
            int unidadesPorEmbalagem,
            boolean conferir
    ) {
        static ItemPreviaResponse from(ImportacaoNfeService.ItemPrevia p) {
            NfeXmlParser.Item i = p.item();
            return new ItemPreviaResponse(i.numero(), i.codigoFornecedor(), i.descricao(), i.ncm(), i.cest(),
                    i.eanComercial(), i.eanTributavel(), i.unidadeComercial(), i.quantidadeComercial(),
                    i.unidadeTributavel(), i.quantidadeTributavel(), i.custoTotal(),
                    p.produtoId(), p.produtoNome(), p.origem(), p.unidadesPorEmbalagem(), p.conferir());
        }
    }

    public record PreviaResponse(
            String chaveAcesso,
            String numero,
            String serie,
            String dataEmissao,
            String fornecedorCnpj,
            String fornecedorNome,
            Long fornecedorId,
            boolean fornecedorInativo,
            String destinatarioNome,
            String destinatarioDocumento,
            BigDecimal valorTotal,
            Long notaExistenteId,
            List<String> avisos,
            List<ItemPreviaResponse> itens
    ) {
        static PreviaResponse from(ImportacaoNfeService.Previa p) {
            NfeXmlParser.Documento d = p.documento();
            return new PreviaResponse(d.chaveAcesso(), d.numero(), d.serie(), d.dataEmissao().toString(),
                    d.emitente().documento(), d.emitente().nome(), p.fornecedorId(), p.fornecedorInativo(),
                    d.destinatario().nome(), d.destinatario().documento(), d.valorTotal(), p.notaExistenteId(),
                    p.avisos(), p.itens().stream().map(ItemPreviaResponse::from).toList());
        }
    }
}
