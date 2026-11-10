
package controller;

import model.Permissao;
import model.Usuario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import repository.UsuarioRepository;

/**
 * Utilitarios de autorizacao utilizados pelos
 * controllers REST do StockOS.
 *
 * Responsabilidades:
 * - Resolver um usuario pelo RU legado;
 * - Rejeitar usuarios inexistentes ou inativos;
 * - Verificar permissoes;
 * - Negar acesso quando os dados sao invalidos.
 *
 * ATENCAO:
 * X-User-RU e fornecido pelo cliente e NAO
 * constitui uma autenticacao segura.
 *
 * Para uso comercial, substituir a resolucao
 * por uma identidade autenticada pelo servidor,
 * com validacao de estabelecimento e filial.
 */
public final class ControllerUtils {

    private static final Logger log =
            LoggerFactory.getLogger(ControllerUtils.class);

    private static final int MAX_RU_DIGITOS = 19;

    private static final String ERRO_ACESSO_NEGADO =
            "Acesso negado.";

    private static final String ERRO_RU_AUSENTE =
            "Identidade ausente. Informe o cabecalho X-User-RU.";

    private static final String ERRO_RU_INVALIDO =
            "Identidade invalida. X-User-RU deve ser numerico e positivo.";

    private ControllerUtils() {
        throw new UnsupportedOperationException(
                "Classe utilitaria nao pode ser instanciada."
        );
    }

    // =====================================================
    // RESOLUCAO DE USUARIO - LEGADO
    // =====================================================

    /**
     * Resolve um usuario a partir do cabecalho
     * X-User-RU.
     *
     * Rejeita:
     * - Cabecalho ausente;
     * - Valor vazio;
     * - Caracteres nao numericos;
     * - RU igual a zero;
     * - RU negativo;
     * - RU maior que Long.MAX_VALUE;
     * - Usuario inexistente;
     * - Usuario inativo.
     *
     * IMPORTANTE:
     * A identidade continua nao autenticada.
     */
    public static Usuario resolveUser(
            String ruHeader,
            UsuarioRepository repo
    ) {
        if (ruHeader == null || ruHeader.isBlank()) {
            log.warn("Requisicao sem X-User-RU.");

            throw new SecurityException(
                    ERRO_RU_AUSENTE
            );
        }

        String valor = ruHeader.trim();

        if (!formatoRuValido(valor)) {
            log.warn(
                    "Requisicao com formato de RU invalido."
            );

            throw new SecurityException(
                    ERRO_RU_INVALIDO
            );
        }

        final long ru;

        try {
            ru = Long.parseLong(valor);

        } catch (NumberFormatException ex) {
            log.warn(
                    "Requisicao com RU fora do intervalo permitido."
            );

            throw new SecurityException(
                    ERRO_RU_INVALIDO
            );
        }

        if (ru <= 0) {
            log.warn(
                    "Requisicao com RU nao positivo."
            );

            throw new SecurityException(
                    ERRO_RU_INVALIDO
            );
        }

        Usuario usuario = repo.buscarPorRu(ru)
                .orElseThrow(() -> {
                    log.warn(
                            "Tentativa de acesso com RU inexistente."
                    );

                    return new SecurityException(
                            "Usuario nao encontrado para o cabecalho X-User-RU."
                    );
                });

        if (!usuario.isAtivo()) {
            log.warn(
                    "Tentativa de acesso com usuario inativo."
            );

            throw new SecurityException(
                    "Usuario inativo."
            );
        }

        return usuario;
    }

    // =====================================================
    // RESOLUCAO COM PERMISSAO ESPECIFICA
    // =====================================================

    /**
     * Resolve o usuario e exige uma permissao.
     */
    public static Usuario resolveUserAndRequirePermission(
            String ruHeader,
            UsuarioRepository repo,
            Permissao permissao,
            String mensagemErro
    ) {
        Usuario usuario = resolveUser(
                ruHeader,
                repo
        );

        requirePermission(
                usuario,
                permissao,
                mensagemErro
        );

        return usuario;
    }

    // =====================================================
    // RESOLUCAO COM MULTIPLAS PERMISSOES
    // =====================================================

    /**
     * Resolve o usuario e exige pelo menos
     * uma das permissoes informadas.
     */
    public static Usuario resolveUserAndRequireAnyPermission(
            String ruHeader,
            UsuarioRepository repo,
            String mensagemErro,
            Permissao... permissoes
    ) {
        Usuario usuario = resolveUser(
                ruHeader,
                repo
        );

        requireAnyPermission(
                usuario,
                mensagemErro,
                permissoes
        );

        return usuario;
    }

    // =====================================================
    // VERIFICACAO DE PERMISSAO ESPECIFICA
    // =====================================================

    /**
     * Exige uma permissao especifica.
     *
     * Nega acesso quando:
     * - Usuario e nulo;
     * - Usuario esta inativo;
     * - Usuario nao possui classe;
     * - Permissao solicitada e nula;
     * - Classe nao possui a permissao.
     */
    public static void requirePermission(
            Usuario usuario,
            Permissao permissao,
            String mensagemErro
    ) {
        if (!usuarioPodeSerAutorizado(usuario)
                || permissao == null
                || !usuario.getClasse()
                        .possuiPermissao(permissao)) {

            throw acessoNegado(mensagemErro);
        }
    }

    // =====================================================
    // VERIFICACAO DE MULTIPLAS PERMISSOES
    // =====================================================

    /**
     * Exige pelo menos uma permissao valida.
     *
     * Nega acesso por padrao quando:
     * - Usuario e nulo ou inativo;
     * - Usuario nao possui classe;
     * - Lista de permissoes e nula;
     * - Lista de permissoes e vazia;
     * - Nenhuma permissao e atendida.
     */
    public static void requireAnyPermission(
            Usuario usuario,
            String mensagemErro,
            Permissao... permissoes
    ) {
        if (!usuarioPodeSerAutorizado(usuario)) {
            throw acessoNegado(mensagemErro);
        }

        if (permissoes == null || permissoes.length == 0) {
            throw acessoNegado(mensagemErro);
        }

        for (Permissao permissao : permissoes) {
            if (permissao != null
                    && usuario.getClasse()
                            .possuiPermissao(permissao)) {
                return;
            }
        }

        throw acessoNegado(mensagemErro);
    }

    // =====================================================
    // VALIDACAO INTERNA DE USUARIO
    // =====================================================

    /**
     * Verifica condicoes minimas para autorizacao.
     *
     * Essa verificacao nao substitui a autenticacao.
     */
    private static boolean usuarioPodeSerAutorizado(
            Usuario usuario
    ) {
        return usuario != null
                && usuario.isAtivo()
                && usuario.getClasse() != null;
    }

    // =====================================================
    // VALIDACAO DE FORMATO DO RU
    // =====================================================

    /**
     * Aceita apenas digitos ASCII.
     *
     * Evita formatos com sinais, letras,
     * espacos internos ou valores excessivos.
     */
    private static boolean formatoRuValido(
            String valor
    ) {
        if (valor.isEmpty()
                || valor.length() > MAX_RU_DIGITOS) {
            return false;
        }

        for (int i = 0; i < valor.length(); i++) {
            char caractere = valor.charAt(i);

            if (caractere < '0' || caractere > '9') {
                return false;
            }
        }

        return true;
    }

    // =====================================================
    // ERROS DE AUTORIZACAO
    // =====================================================

    /**
     * Garante uma mensagem padrao quando
     * nenhuma mensagem especifica foi fornecida.
     */
    private static SecurityException acessoNegado(
            String mensagemErro
    ) {
        String mensagem = mensagemErro;

        if (mensagem == null || mensagem.isBlank()) {
            mensagem = ERRO_ACESSO_NEGADO;
        }

        return new SecurityException(mensagem);
    }
}

