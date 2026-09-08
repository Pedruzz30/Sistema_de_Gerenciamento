package service;

import model.GrupoModificador;
import model.LogAcao;
import model.TipoSelecaoModificador;
import model.Usuario;
import org.springframework.transaction.annotation.Transactional;
import repository.GrupoModificadorRepository;
import repository.LogRepository;

import java.util.List;

@Transactional(readOnly = true)
public class GrupoModificadorService {

    private final GrupoModificadorRepository grupoRepository;
    private final LogRepository logRepository;

    public GrupoModificadorService(GrupoModificadorRepository grupoRepository,
                                   LogRepository logRepository) {
        this.grupoRepository = grupoRepository;
        this.logRepository = logRepository;
    }

    @Transactional
    public GrupoModificador criar(String nome,
                                  String descricao,
                                  String codigo,
                                  TipoSelecaoModificador tipoSelecao,
                                  Boolean obrigatorio,
                                  Integer quantidadeMinima,
                                  Integer quantidadeMaxima,
                                  Boolean permiteRepeticao,
                                  Boolean ativo,
                                  Integer ordemExibicao,
                                  Usuario usuario) {
        validarCodigoUnico(codigo, null);
        GrupoModificador grupo = grupoRepository.salvar(new GrupoModificador(
                0,
                nome,
                descricao,
                codigo,
                tipoSelecao,
                obrigatorio,
                quantidadeMinima,
                quantidadeMaxima,
                permiteRepeticao,
                ativo,
                ordemExibicao,
                usuario == null ? null : usuario.getRu()
        ));
        auditar(usuario, "GRUPO_MODIFICADOR_CRIADO", "Grupo de modificador criado: " + grupo.getNome());
        return grupo;
    }

    @Transactional
    public GrupoModificador atualizar(long id,
                                      String nome,
                                      String descricao,
                                      String codigo,
                                      TipoSelecaoModificador tipoSelecao,
                                      Boolean obrigatorio,
                                      Integer quantidadeMinima,
                                      Integer quantidadeMaxima,
                                      Boolean permiteRepeticao,
                                      Boolean ativo,
                                      Integer ordemExibicao,
                                      Usuario usuario) {
        GrupoModificador grupo = buscarObrigatorio(id);
        validarCodigoUnico(codigo, id);
        grupo.atualizar(nome, descricao, codigo, tipoSelecao, obrigatorio, quantidadeMinima,
                quantidadeMaxima, permiteRepeticao, ativo, ordemExibicao);
        grupo = grupoRepository.salvar(grupo);
        auditar(usuario, "GRUPO_MODIFICADOR_ATUALIZADO", "Grupo de modificador atualizado: " + grupo.getNome());
        return grupo;
    }

    @Transactional
    public GrupoModificador alterarStatus(long id, boolean ativo, Usuario usuario) {
        GrupoModificador grupo = buscarObrigatorio(id);
        grupo.alterarStatus(ativo);
        grupo = grupoRepository.salvar(grupo);
        auditar(usuario, "GRUPO_MODIFICADOR_STATUS", "Grupo de modificador " + (ativo ? "ativado: " : "inativado: ") + grupo.getNome());
        return grupo;
    }

    public GrupoModificador buscarObrigatorio(long id) {
        return grupoRepository.buscarPorId(id)
                .orElseThrow(() -> new ErroNegocioException(
                        "GRUPO_MODIFICADOR_NAO_ENCONTRADO",
                        "Grupo de modificador nao encontrado."
                ));
    }

    public List<GrupoModificador> listar() {
        return grupoRepository.listarTodos();
    }

    private void validarCodigoUnico(String codigo, Long ignorarId) {
        if (codigo != null && grupoRepository.existeCodigo(codigo, ignorarId)) {
            throw new ErroNegocioException("CONFIGURACAO_ITEM_INVALIDA", "Codigo de grupo de modificador ja existe.");
        }
    }

    private void auditar(Usuario usuario, String acao, String descricao) {
        logRepository.salvar(new LogAcao(0, usuario == null ? null : usuario.getRu(), acao, descricao));
    }
}
