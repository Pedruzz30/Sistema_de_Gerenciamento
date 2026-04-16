package model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Converter
public class PermissoesStringConverter implements AttributeConverter<Set<Permissao>, String> {

    @Override
    public String convertToDatabaseColumn(Set<Permissao> permissoes) {
        if (permissoes == null || permissoes.isEmpty()) {
            return "";
        }

        return permissoes.stream()
                .filter(java.util.Objects::nonNull)
                .sorted()
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    @Override
    public Set<Permissao> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) {
            return EnumSet.noneOf(Permissao.class);
        }

        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(Permissao::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Permissao.class)));
    }
}
