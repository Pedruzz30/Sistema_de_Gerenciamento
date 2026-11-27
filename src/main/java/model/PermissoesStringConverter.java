package model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.EnumSet;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Converte permissões de acesso entre o modelo de domínio
 * e sua representação textual no banco de dados.
 *
 * <p>As permissões são armazenadas pelos nomes das constantes
 * do enum, separadas por vírgulas e em ordem determinística.</p>
 *
 * <p>Permissões desconhecidas são rejeitadas durante a leitura,
 * evitando que inconsistências sejam ignoradas silenciosamente.</p>
 */
@Converter
public class PermissoesStringConverter
        implements AttributeConverter<Set<Permissao>, String> {

    private static final String SEPARADOR = ",";
    private static final String VALOR_VAZIO = "";

    @Override
    public String convertToDatabaseColumn(Set<Permissao> permissoes) {
        if (permissoes == null || permissoes.isEmpty()) {
            return VALOR_VAZIO;
        }

        EnumSet<Permissao> permissoesValidas =
                EnumSet.noneOf(Permissao.class);

        for (Permissao permissao : permissoes) {
            if (permissao != null) {
                permissoesValidas.add(permissao);
            }
        }

        StringJoiner resultado = new StringJoiner(SEPARADOR);

        for (Permissao permissao : permissoesValidas) {
            resultado.add(permissao.name());
        }

        return resultado.toString();
    }

    @Override
    public Set<Permissao> convertToEntityAttribute(String value) {
        EnumSet<Permissao> permissoes =
                EnumSet.noneOf(Permissao.class);

        if (value == null || value.isBlank()) {
            return permissoes;
        }

        for (String fragmento : value.split(SEPARADOR)) {
            String token = fragmento.trim();

            if (!token.isEmpty()) {
                permissoes.add(Permissao.valueOf(token));
            }
        }

        return permissoes;
    }
}
