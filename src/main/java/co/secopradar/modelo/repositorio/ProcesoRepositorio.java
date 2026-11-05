package co.secopradar.modelo.repositorio;

import co.secopradar.modelo.Proceso;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Procesos de SECOP II. Los servicios dependen de esta interfaz, no de cómo se
 * guardan los datos: hoy la implementa ProcesoDAO (JDBC: H2 o Supabase).
 */
public interface ProcesoRepositorio {

    /** Fila lista para guardar: el proceso más los campos extra de la fuente. */
    record Fila(Proceso proceso, String estadoProcedimiento, Integer respuestas, String raw) {
    }

    /** Procesos vigentes: sin fecha de cierre o que cerraron hace menos de 2 días. */
    List<Proceso> vigentes();

    Set<String> existentes(Collection<String> ids);

    void insertar(List<Fila> filas);

    /** Lo que cambia en la fuente: estado, cierre, valor, entidad y nombre (objeto se conserva). */
    void actualizar(List<Fila> filas);

    long contar();

    OffsetDateTime ultimoIngreso();

    /** Procesos abiertos (estado Abierto y cierre que no ha pasado) por departamento y tipo de contrato. */
    List<Map<String, Object>> abiertosPorDepartamentoYTipo(OffsetDateTime ahora);

    Map<String, Object> resumenAbiertos(OffsetDateTime ahora, OffsetDateTime inicioHoy);

    /**
     * Ofertas recibidas en mínima cuantía, en procesos ya cerrados y en
     * evaluación o adjudicados (donde SECOP registra bien las respuestas).
     */
    Map<String, Object> ofertasMinimaCuantia(OffsetDateTime ahora);

    List<Proceso> porIds(Collection<String> ids);
}
