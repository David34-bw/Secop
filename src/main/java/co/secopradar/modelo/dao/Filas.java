package co.secopradar.modelo.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

/** Ayudas para leer columnas de un ResultSet. */
final class Filas {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> LISTA = new TypeReference<>() {
    };

    private Filas() {
    }

    static OffsetDateTime fecha(ResultSet rs, String col) throws SQLException {
        return rs.getObject(col, OffsetDateTime.class);
    }

    static OffsetDateTime fecha(ResultSet rs, int col) throws SQLException {
        return rs.getObject(col, OffsetDateTime.class);
    }

    static Integer entero(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    static Long largo(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    static List<String> lista(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JSON.readValue(json, LISTA);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    static String json(List<String> lista) {
        try {
            return JSON.writeValueAsString(lista == null ? List.of() : lista);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
