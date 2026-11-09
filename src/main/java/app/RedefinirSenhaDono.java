package app;

import service.SenhaService;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Recupera o acesso quando o dono esquece a senha e não há outro dono para redefinir.
 * Roda no próprio notebook da loja (quem chega aqui já tem acesso ao computador e ao banco),
 * pelo atalho "Redefinir senha do dono", que passa DB_URL, DB_USERNAME e DB_PASSWORD.
 *
 * <p>Saída sem acento: o console do Windows não mostra UTF-8 por padrão.
 */
public final class RedefinirSenhaDono {

    private RedefinirSenhaDono() {}

    record Dono(long ru, String nome) {}

    public static void main(String[] args) {
        String url = System.getenv("DB_URL");
        if (url == null || url.isBlank()) {
            System.out.println("ERRO: banco de dados nao informado.");
            System.exit(1);
        }
        Long escolhido = null;
        if (args.length > 0) {
            try {
                escolhido = Long.parseLong(args[0].trim());
            } catch (NumberFormatException e) {
                System.out.println("ERRO: numero do dono invalido: " + args[0]);
                System.exit(1);
            }
        }
        try (Connection conexao = DriverManager.getConnection(url, System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"))) {
            System.exit(executar(conexao, escolhido));
        } catch (SQLException e) {
            System.out.println("ERRO: nao foi possivel falar com o banco: " + e.getMessage());
            System.exit(1);
        }
    }

    static int executar(Connection conexao, Long escolhido) throws SQLException {
        List<Dono> donos = new ArrayList<>();
        try (PreparedStatement consulta = conexao.prepareStatement(
                "SELECT ru, nome, sobrenome FROM usuarios WHERE perfil = 'ADMIN' AND ativo ORDER BY ru");
             ResultSet linhas = consulta.executeQuery()) {
            while (linhas.next()) {
                donos.add(new Dono(linhas.getLong("ru"), linhas.getString("nome") + " " + linhas.getString("sobrenome")));
            }
        }
        if (donos.isEmpty()) {
            System.out.println("ERRO: nenhum dono ativo encontrado.");
            return 2;
        }
        Dono alvo;
        if (escolhido != null) {
            alvo = donos.stream().filter(d -> d.ru() == escolhido).findFirst().orElse(null);
            if (alvo == null) {
                System.out.println("ERRO: nao ha dono ativo com o numero " + escolhido + ".");
                listar(donos);
                return 3;
            }
        } else if (donos.size() > 1) {
            System.out.println("Ha mais de um dono. Rode de novo informando o numero:");
            listar(donos);
            return 3;
        } else {
            alvo = donos.get(0);
        }

        String temporaria = SenhaService.gerarTemporaria();
        boolean autoCommit = conexao.getAutoCommit();
        conexao.setAutoCommit(false);
        try {
            try (PreparedStatement senha = conexao.prepareStatement(
                    "UPDATE usuarios SET senha = ?, senha_provisoria = TRUE WHERE ru = ?")) {
                senha.setString(1, new SenhaService().gerarHash(temporaria));
                senha.setLong(2, alvo.ru());
                senha.executeUpdate();
            }
            try (PreparedStatement sessoes = conexao.prepareStatement(
                    "UPDATE sessoes_usuario SET expira_em = now() WHERE usuario_ru = ? AND expira_em > now()")) {
                sessoes.setLong(1, alvo.ru());
                sessoes.executeUpdate();
            }
            try (PreparedStatement log = conexao.prepareStatement(
                    "INSERT INTO logs_auditoria (usuario_ru, acao, descricao, data_hora) VALUES (?, 'SENHA_REDEFINIDA', ?, now())")) {
                log.setLong(1, alvo.ru());
                log.setString(2, "Senha do dono " + alvo.nome() + " redefinida no notebook da loja.");
                log.executeUpdate();
            }
            conexao.commit();
        } catch (SQLException e) {
            conexao.rollback();
            throw e;
        } finally {
            conexao.setAutoCommit(autoCommit);
        }
        System.out.println("Dono: " + alvo.nome() + " (numero " + alvo.ru() + ")");
        System.out.println("SENHA TEMPORARIA: " + temporaria);
        return 0;
    }

    private static void listar(List<Dono> donos) {
        for (Dono dono : donos) {
            System.out.println("  " + dono.ru() + " - " + dono.nome());
        }
    }
}
