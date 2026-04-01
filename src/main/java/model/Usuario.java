package model;

import java.util.Objects;

/**
 * Representa um usuário do sistema — seja um administrador ou um operador.
 *
 * <h2>Identidade</h2>
 * O {@code ru} (Registro de Usuário) é o identificador único. {@code ru == 0}
 * indica que o usuário ainda não foi persistido. Use {@link #comRu(long)} para
 * que o repositório retorne a cópia com o RU definitivo sem expor um setter.
 *
 * <h2>Autenticação</h2>
 * {@link #autenticar} compara nome + sobrenome (case-insensitive) e senha
 * (case-sensitive). A senha é armazenada em texto simples — aceitável para
 * este projeto de aprendizado, mas em produção deve ser substituída por hash
 * bcrypt ou Argon2 via {@code spring-security-crypto}.
 *
 * <h2>CPF</h2>
 * Normalizado para 11 dígitos sem formatação e validado pelo algoritmo da
 * Receita Federal (módulo 11, dois dígitos verificadores) — o mesmo utilizado
 * em {@link Fornecedor} para o CNPJ.
 *
 * <p><strong>Atenção para o seed de AppConfig:</strong> CPFs como
 * {@code "000.000.000-00"} falham na validação por serem sequências
 * homogêneas. Substitua pelos CPFs de teste documentados em
 * {@link #cpfDigitosValidos(String)}.
 *
 * <h2>Permissões</h2>
 * As permissões efetivas são determinadas pela {@link ClasseFuncionario} — não
 * pelo {@link Perfil}. O perfil ({@code ADMIN} / {@code OPERADOR}) é um rótulo
 * de exibição, não uma fonte de autorização.
 */
public class Usuario {

    // ── Enum de perfil ────────────────────────────────────────────────────────

    /**
     * Rótulo de exibição do tipo de usuário.
     *
     * <p>Não use {@code Perfil} para decisões de autorização — use
     * {@link ClasseFuncionario#possuiPermissao(Permissao)} para isso.
     */
    public enum Perfil {
        /** Usuário com acesso administrativo ao sistema. */
        ADMIN,
        /** Usuário operacional com acesso restrito pelas permissões da classe. */
        OPERADOR
    }

    // ── Campos imutáveis ──────────────────────────────────────────────────────

    private final long   ru;
    private final String nome;
    private final String sobrenome;
    private final String cpf;      // 11 dígitos sem formatação, validado
    private final String senha;    // ⚠ texto simples — hash em produção
    private final Perfil perfil;

    // ── Campos mutáveis ───────────────────────────────────────────────────────

    private ClasseFuncionario classe;
    private boolean           ativo;

    // ── Construtor ────────────────────────────────────────────────────────────

    /**
     * Cria um novo usuário no estado ativo.
     *
     * <p>{@code ru == 0} indica que o usuário ainda não foi persistido.
     * IDs negativos são rejeitados. Use {@link #comRu(long)} para que o
     * repositório retorne a cópia com o RU definitivo.
     *
     * @param ru       registro de usuário; {@code >= 0} (0 = não persistido)
     * @param nome     primeiro nome; não pode ser nulo ou vazio
     * @param sobrenome sobrenome; não pode ser nulo ou vazio
     * @param cpf      CPF com ou sem formatação; deve ter 11 dígitos e passar
     *                 na validação da Receita Federal
     * @param senha    senha de acesso; não pode ser nula ou vazia
     * @param classe   classe de funcionário com permissões; pode ser {@code null}
     *                 para usuários ainda sem classe atribuída
     * @param perfil   rótulo de perfil ({@code ADMIN} / {@code OPERADOR});
     *                 pode ser {@code null}
     * @throws IllegalArgumentException se qualquer argumento obrigatório for inválido
     */
    public Usuario(long ru,
                   String nome,
                   String sobrenome,
                   String cpf,
                   String senha,
                   ClasseFuncionario classe,
                   Perfil perfil) {
        validar(ru >= 0,                     "RU não pode ser negativo.");
        validar(nome != null && !nome.isBlank(),         "Nome é obrigatório.");
        validar(sobrenome != null && !sobrenome.isBlank(),"Sobrenome é obrigatório.");
        validar(senha != null && !senha.isBlank(),        "Senha é obrigatória.");

        String cpfNormalizado = normalizarCpf(cpf);
        validar(cpfDigitosValidos(cpfNormalizado), "CPF inválido (dígitos verificadores incorretos).");

        this.ru        = ru;
        this.nome      = nome.trim();
        this.sobrenome = sobrenome.trim();
        this.cpf       = cpfNormalizado;
        this.senha     = senha;
        this.classe    = classe;
        this.perfil    = perfil;
        this.ativo     = true;
    }

    /**
     * Construtor privado usado por {@link #comRu} para preservar estado
     * mutável sem revalidar campos já validados.
     */
    private Usuario(long ru, String nome, String sobrenome, String cpf,
                    String senha, ClasseFuncionario classe, Perfil perfil,
                    boolean ativo) {
        this.ru        = ru;
        this.nome      = nome;
        this.sobrenome = sobrenome;
        this.cpf       = cpf;
        this.senha     = senha;
        this.classe    = classe;
        this.perfil    = perfil;
        this.ativo     = ativo;
    }

    // ── Fábrica para repositório ──────────────────────────────────────────────

    /**
     * Retorna uma cópia deste usuário com o RU substituído, preservando
     * todos os campos mutáveis ({@code classe}, {@code ativo}).
     *
     * <p>Permite que o repositório atribua o RU definitivo sem expor
     * um setter e sem recriar o estado do usuário manualmente:
     * <pre>
     *   // InMemoryUsuarioRepository.salvar():
     *   Usuario persistido = usuario.comRu(sequenceRu.getAndIncrement());
     *   lista.add(persistido);
     *   return persistido;
     * </pre>
     *
     * @param novoRu RU definitivo; deve ser {@code > 0}
     * @return novo {@code Usuario} com o RU fornecido e mesmo estado
     */
    public Usuario comRu(long novoRu) {
        validar(novoRu > 0, "Novo RU deve ser maior que zero.");
        return new Usuario(novoRu, nome, sobrenome, cpf, senha, classe, perfil, ativo);
    }

    // ── Autenticação ──────────────────────────────────────────────────────────

    /**
     * Verifica se as credenciais fornecidas correspondem a este usuário.
     *
     * <p>Nome e sobrenome são comparados sem distinção de maiúsculas/minúsculas
     * (case-insensitive). A senha é comparada de forma exata (case-sensitive).
     *
     * @param nome      nome informado na tentativa de login
     * @param sobrenome sobrenome informado na tentativa de login
     * @param senha     senha informada na tentativa de login
     * @return {@code true} se as três credenciais coincidirem
     */
    public boolean autenticar(String nome, String sobrenome, String senha) {
        return this.nome.equalsIgnoreCase(nome)
                && this.sobrenome.equalsIgnoreCase(sobrenome)
                && this.senha.equals(senha);
    }

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    /**
     * Desativa o usuário, impedindo login e operações em seu nome.
     *
     * <p>Usuários nunca são excluídos — o histórico de logs e notas
     * fiscais depende de sua existência.
     *
     * @throws IllegalStateException se o usuário já estiver inativo
     */
    public void desativar() {
        validarTransicaoAtivo(true, "Usuário '" + getNomeCompleto() + "' já está inativo.");
        this.ativo = false;
    }

    /**
     * Reativa um usuário previamente desativado.
     *
     * @throws IllegalStateException se o usuário já estiver ativo
     */
    public void reativar() {
        validarTransicaoAtivo(false, "Usuário '" + getNomeCompleto() + "' já está ativo.");
        this.ativo = true;
    }

    /**
     * Altera a classe de funcionário (promoção ou rebaixamento de cargo).
     *
     * @param novaClasse nova classe com as permissões correspondentes;
     *                   não pode ser {@code null}
     * @throws IllegalArgumentException se {@code novaClasse} for nulo
     */
    public void alterarClasse(ClasseFuncionario novaClasse) {
        validar(novaClasse != null, "Classe do funcionário é obrigatória.");
        this.classe = novaClasse;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public long             getRu()        { return ru;        }
    public String           getNome()      { return nome;      }
    public String           getSobrenome() { return sobrenome; }
    public ClasseFuncionario getClasse()   { return classe;    }
    public Perfil           getPerfil()    { return perfil;    }
    public boolean          isAtivo()      { return ativo;     }

    /** @return nome completo no formato "Nome Sobrenome" */
    public String getNomeCompleto() {
        return nome + " " + sobrenome;
    }

    /** Retorna o CPF formatado no padrão {@code XXX.XXX.XXX-XX}. */
    public String getCpf() {
        return formatarCpf(cpf);
    }

    /** Retorna o CPF como 11 dígitos sem formatação. */
    public String getCpfBruto() {
        return cpf;
    }

    /**
     * Expõe a senha para autenticação interna e persistência pelo repositório.
     *
     * <p><strong>⚠ Atenção:</strong> este método expõe a senha em texto simples.
     * Em produção, substitua por hash (bcrypt/Argon2) e nunca exponha via API.
     */
    public String getSenhaInterna() {
        return senha;
    }

    // ── Infraestrutura de objeto ──────────────────────────────────────────────

    @Override
    public String toString() {
        return String.format(
                "Usuario{ru=%d, nome='%s', cpf='%s', perfil=%s, classe=%s, ativo=%b}",
                ru,
                getNomeCompleto(),
                mascararCpf(cpf),
                perfil,
                classe != null ? classe.getNome() : "N/A",
                ativo
        );
    }

    /**
     * Dois usuários são iguais se tiverem o mesmo {@code ru}.
     * Usuários com {@code ru == 0} (não persistidos) nunca são iguais entre si.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Usuario other)) return false;
        return ru != 0 && ru == other.ru;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ru);
    }

    // ── Auxiliares privados ───────────────────────────────────────────────────

    /** Centraliza a validação de transição ativo/inativo. */
    private void validarTransicaoAtivo(boolean esperado, String mensagem) {
        if (ativo == esperado) throw new IllegalStateException(mensagem);
    }

    /**
     * Remove caracteres não-numéricos e valida que o CPF tem 11 dígitos.
     *
     * @throws IllegalArgumentException se o CPF não contiver exatamente 11 dígitos
     */
    private static String normalizarCpf(String cpf) {
        validar(cpf != null && !cpf.isBlank(), "CPF é obrigatório.");
        String digitos = cpf.replaceAll("\\D", "");
        validar(digitos.length() == 11,
                "CPF deve conter 11 dígitos. Recebido: " + digitos.length() + " dígito(s).");
        return digitos;
    }

    /**
     * Valida os dígitos verificadores do CPF pelo algoritmo da Receita Federal.
     *
     * <h3>Como funciona</h3>
     * O CPF tem dois dígitos verificadores (posições 9 e 10):
     * <pre>
     *   Pesos do 1º dígito: 10  9  8  7  6  5  4  3  2
     *   Pesos do 2º dígito: 11 10  9  8  7  6  5  4  3  2
     *
     *   Resto = (soma × 10) % 11
     *   Dígito = (Resto == 10 || Resto == 11) ? 0 : Resto
     * </pre>
     *
     * <p>Sequências homogêneas ({@code 000.000.000-00}, {@code 111.111.111-11} etc.)
     * são rejeitadas — são CPFs fictícios válidos matematicamente mas inaceitáveis
     * em produção.
     *
     * <p><strong>CPFs válidos para dados de teste:</strong>
     * {@code "529.982.247-25"}, {@code "111.444.777-35"}, {@code "123.456.789-09"}.
     *
     * @param digitos 11 dígitos numéricos sem formatação
     * @return {@code true} se os dígitos verificadores estiverem corretos
     */
    private static boolean cpfDigitosValidos(String digitos) {
        if (digitos.chars().distinct().count() == 1) return false;

        return digitoCpfValido(digitos, 9,  new int[]{10,9,8,7,6,5,4,3,2})
                && digitoCpfValido(digitos, 10, new int[]{11,10,9,8,7,6,5,4,3,2});
    }

    /** Calcula e compara um único dígito verificador do CPF. */
    private static boolean digitoCpfValido(String digitos, int posicao, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += Character.getNumericValue(digitos.charAt(i)) * pesos[i];
        }
        int resto    = (soma * 10) % 11;
        int esperado = (resto == 10 || resto == 11) ? 0 : resto;
        return Character.getNumericValue(digitos.charAt(posicao)) == esperado;
    }

    /** Formata 11 dígitos no padrão oficial {@code XXX.XXX.XXX-XX}. */
    private static String formatarCpf(String digitos) {
        return String.format("%s.%s.%s-%s",
                digitos.substring(0, 3),
                digitos.substring(3, 6),
                digitos.substring(6, 9),
                digitos.substring(9)
        );
    }

    /**
     * Máscara para logs públicos: exibe apenas os 3 primeiros e os 2 últimos dígitos.
     * <pre>  52998224725  →  529.***.***-25  </pre>
     */
    private static String mascararCpf(String digitos) {
        return digitos.substring(0, 3) + ".***.***-" + digitos.substring(9);
    }

    /** Lança {@link IllegalArgumentException} se a condição for falsa. */
    private static void validar(boolean condicao, String mensagem) {
        if (!condicao) throw new IllegalArgumentException(mensagem);
    }
}