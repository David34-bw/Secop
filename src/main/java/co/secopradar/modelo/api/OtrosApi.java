package co.secopradar.modelo.api;

import co.secopradar.modelo.Documento;
import co.secopradar.modelo.Postulacion;
import co.secopradar.modelo.Proceso;
import co.secopradar.modelo.Proponente;
import co.secopradar.modelo.TipoDocumento;
import co.secopradar.modelo.repositorio.DocumentoRepositorio;
import co.secopradar.modelo.repositorio.LogRepositorio;
import co.secopradar.modelo.repositorio.PostulacionRepositorio;
import co.secopradar.modelo.repositorio.ProcesoOcultoRepositorio;
import co.secopradar.modelo.repositorio.ProponenteRepositorio;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static co.secopradar.modelo.api.ClienteApi.cuerpo;

/** Repositorios por la API más pequeños: historial, ocultos, proponente, documentos y postulaciones. */
public final class OtrosApi {

    private OtrosApi() {
    }

    /** Historial de la actualización (Administración). */
    public static class LogApi implements LogRepositorio {
        private final ClienteApi api;

        public LogApi(ClienteApi api) {
            this.api = api;
        }

        @Override
        public void ingesta(int leidos, int nuevos, int actualizados, boolean exito, String error) {
            throw new SoloServidor("registrar la ingesta");
        }

        @Override
        public void coincidencias(int perfiles, int procesos, int nuevas, int quitadas, boolean exito, String error) {
            throw new SoloServidor("registrar el cruce");
        }

        /** Solo las dos tablas de historial; el servidor no acepta nombres de tabla. */
        @Override
        public List<Map<String, Object>> ultimas(String tabla, int n) {
            String tipo = switch (tabla) {
                case "ingesta_log" -> "ingesta";
                case "coincidencias_log" -> "coincidencias";
                default -> throw new IllegalArgumentException("Historial desconocido: " + tabla);
            };
            List<Map<String, Object>> filas = new ArrayList<>();
            for (JsonNode f : api.llamar("admin.historial", cuerpo("tipo", tipo, "n", n)).path("filas")) {
                Map<String, Object> m = ClienteApi.JSON.convertValue(f, new TypeReference<LinkedHashMap<String, Object>>() {
                });
                // Igual que BaseDatos.filas: las fechas como OffsetDateTime.
                m.replaceAll((k, v) -> k.endsWith("_at") && v instanceof String s ? OffsetDateTime.parse(s) : v);
                filas.add(m);
            }
            return filas;
        }
    }

    public static class ProcesoOcultoApi implements ProcesoOcultoRepositorio {
        private final ClienteApi api;

        public ProcesoOcultoApi(ClienteApi api) {
            this.api = api;
        }

        @Override
        public void ocultar(int usuarioId, String procesoId) {
            api.llamar("oportunidades.ocultar", cuerpo("procesoId", procesoId));
        }

        @Override
        public void mostrar(int usuarioId, String procesoId) {
            api.llamar("oportunidades.mostrar", cuerpo("procesoId", procesoId));
        }

        @Override
        public Set<String> deUsuario(int usuarioId) {
            return new HashSet<>(api.lista("oportunidades.ocultos", null, "procesos", String.class));
        }
    }

    public static class ProponenteApi implements ProponenteRepositorio {
        private final ClienteApi api;

        public ProponenteApi(ClienteApi api) {
            this.api = api;
        }

        @Override
        public Optional<Proponente> deUsuario(int usuarioId) {
            return Optional.of(api.llamar("proponente.obtener", null, null, Proponente.class));
        }

        /** El servidor vuelve a validar y guarda todo en un solo upsert. */
        @Override
        public void guardar(int usuarioId, Proponente p) {
            try {
                api.llamar("proponente.guardar", cuerpo("proponente", p));
            } catch (ClienteApi.ErrorApi e) {
                if (!e.campos.isEmpty()) throw new co.secopradar.modelo.servicio.DocumentosService.ErrorDatos(e.campos);
                throw e;
            }
        }
    }

    /**
     * Documentos por la API. Subir es un flujo propio (pedir URL, PUT a
     * Storage, confirmar) que hace DocumentosServiceApi; aquí está el resto.
     */
    public static class DocumentoApi implements DocumentoRepositorio {
        private final ClienteApi api;

        public DocumentoApi(ClienteApi api) {
            this.api = api;
        }

        @Override
        public List<Documento> deUsuario(int usuarioId) {
            return api.lista("documentos.listar", null, "documentos", Documento.class);
        }

        @Override
        public Optional<Documento> porId(int id, int usuarioId) {
            return deUsuario(usuarioId).stream().filter(d -> d.id() == id).findFirst();
        }

        @Override
        public Optional<byte[]> contenido(int id, int usuarioId) {
            try {
                return Optional.of(api.contenidoDe(api.llamar("documentos.descargar", cuerpo("id", id))));
            } catch (ClienteApi.ErrorApi e) {
                if (e.estado == 404) return Optional.empty();
                throw e;
            }
        }

        @Override
        public int crear(int usuarioId, TipoDocumento tipo, String nombreArchivo, String tipoMime, byte[] contenido, LocalDate vence) {
            throw new SoloServidor("registrar archivos (documentos.confirmarSubida)");
        }

        @Override
        public void eliminarTipo(int usuarioId, TipoDocumento tipo) {
            throw new SoloServidor("reemplazar un documento (documentos.confirmarSubida)");
        }

        @Override
        public boolean eliminar(int id, int usuarioId) {
            return api.llamar("documentos.eliminar", cuerpo("id", id)).path("ok").asBoolean();
        }

        @Override
        public void cambiarVence(int id, int usuarioId, LocalDate vence) {
            api.llamar("documentos.cambiarVence", cuerpo("id", id, "vence", vence == null ? null : vence.toString()));
        }
    }

    /**
     * Postulaciones por la API: lecturas, crear y borrar. Los cambios de
     * etapa van por PostulacionesServiceApi (una acción = una llamada,
     * validada en el servidor).
     */
    public static class PostulacionApi implements PostulacionRepositorio {
        private final ClienteApi api;

        public PostulacionApi(ClienteApi api) {
            this.api = api;
        }

        @Override
        public List<Postulacion> deUsuario(int usuarioId) {
            List<Postulacion> l = new ArrayList<>();
            for (JsonNode n : api.llamar("postulaciones.listar", null).path("postulaciones")) {
                l.add(ClienteApi.convertir(n.path("postulacion"), Postulacion.class));
            }
            return l;
        }

        @Override
        public Optional<Postulacion> porId(int id, int usuarioId) {
            try {
                return Optional.of(ClienteApi.convertir(api.llamar("postulaciones.obtener", cuerpo("id", id)).path("postulacion"), Postulacion.class));
            } catch (ClienteApi.ErrorApi e) {
                if (e.estado == 404) return Optional.empty();
                throw e;
            }
        }

        @Override
        public Map<String, Integer> idsPorProceso(int usuarioId) {
            return ClienteApi.JSON.convertValue(api.llamar("postulaciones.idsPorProceso", null),
                    new TypeReference<LinkedHashMap<String, Integer>>() {
                    });
        }

        @Override
        public int crear(int usuarioId, Proceso p) {
            return api.llamar("postulaciones.crear", cuerpo("procesoId", p.id())).path("id").asInt();
        }

        /** postulaciones.obtener ya pone al día los datos del proceso en el servidor. */
        @Override
        public void refrescar(int id, Proceso p) {
        }

        private static SoloServidor porAccion() {
            return new SoloServidor("cambiar etapas (postulaciones.actualizar)");
        }

        @Override public void marcarRevisado(int id, int usuarioId, boolean v) { throw porAccion(); }

        @Override public void marcarPliego(int id, int usuarioId, boolean v, String notas) { throw porAccion(); }

        @Override public void guardarNotas(int id, int usuarioId, String notas) { throw porAccion(); }

        @Override public void guardarRequisitos(int id, int usuarioId, List<Postulacion.Requisito> requisitos) { throw porAccion(); }

        @Override public void guardarPropuesta(int id, int usuarioId, Long valor) { throw porAccion(); }

        @Override public void guardarArchivoPropuesta(int id, int usuarioId, String nombre, byte[] contenido) { throw porAccion(); }

        @Override public void marcarPresentada(int id, int usuarioId, OffsetDateTime cuando) { throw porAccion(); }

        @Override public void guardarResultado(int id, int usuarioId, Postulacion.Resultado r, LocalDate plazo) { throw porAccion(); }

        @Override public void marcarDesistida(int id, int usuarioId, boolean v) { throw porAccion(); }

        @Override
        public Optional<byte[]> archivoPropuesta(int id, int usuarioId) {
            try {
                return Optional.of(api.contenidoDe(api.llamar("postulaciones.descargarPropuesta", cuerpo("id", id))));
            } catch (ClienteApi.ErrorApi e) {
                if (e.estado == 404) return Optional.empty();
                throw e;
            }
        }

        @Override
        public void eliminar(int id, int usuarioId) {
            api.llamar("postulaciones.eliminar", cuerpo("id", id));
        }
    }
}
