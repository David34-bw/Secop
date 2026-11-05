package co.secopradar.modelo.servicio;

import co.secopradar.modelo.BorradorPerfil;
import co.secopradar.modelo.Documento;
import co.secopradar.modelo.Plan;
import co.secopradar.modelo.Postulacion;
import co.secopradar.modelo.TipoDocumento;
import co.secopradar.modelo.Usuario;
import co.secopradar.modelo.api.ClienteApi;
import co.secopradar.modelo.api.ClienteApi.ErrorApi;
import co.secopradar.modelo.repositorio.CoincidenciaRepositorio;
import co.secopradar.modelo.repositorio.DocumentoRepositorio;
import co.secopradar.modelo.repositorio.PerfilRepositorio;
import co.secopradar.modelo.repositorio.PostulacionRepositorio;
import co.secopradar.modelo.repositorio.ProcesoOcultoRepositorio;
import co.secopradar.modelo.repositorio.ProcesoRepositorio;
import co.secopradar.modelo.repositorio.ProponenteRepositorio;
import co.secopradar.modelo.repositorio.UsuarioRepositorio;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static co.secopradar.modelo.api.ClienteApi.cuerpo;

/**
 * Servicios en modo API. Solo cambian las operaciones que en JDBC hacen
 * varias escrituras seguidas: aquí cada una es una sola llamada y el
 * servidor la hace en una transacción. Lo demás se hereda tal cual.
 */
public final class ServiciosApi {

    private ServiciosApi() {
    }

    /** Crear perfil: límite del plan, guardar y cruce en una sola operación del servidor. */
    public static class Perfiles extends PerfilesService {
        private final ClienteApi api;

        public Perfiles(PerfilRepositorio perfiles, PlanesService planes, CoincidenciasService cruce, ClienteApi api) {
            super(perfiles, planes, cruce);
            this.api = api;
        }

        @Override
        public Creado crear(BorradorPerfil b, Usuario u) {
            List<ErrorCampo> errores = validar(b);
            if (!errores.isEmpty()) throw new ErrorPerfil(errores.get(0));
            try {
                JsonNode r = api.llamar("perfiles.crear", cuerpo("perfil", aPerfil(b, u.id())));
                return new Creado(r.path("id").asInt(), r.path("coincidencias").asInt());
            } catch (ErrorApi e) {
                throw new ErrorPerfil(new ErrorCampo("general", e.getMessage()));
            }
        }
    }

    /** Ajustar perfiles al plan lo hace el servidor (al borrar un perfil, al cambiar el plan y en el job). */
    public static class Planes extends PlanesService {
        public Planes(UsuarioRepositorio usuarios, PerfilRepositorio perfiles) {
            super(usuarios, perfiles);
        }

        @Override
        public Resultado sincronizarPerfiles(int userId, Plan plan) {
            return new Resultado(0, 0);
        }

        @Override
        public void ajustarTodos() {
        }
    }

    /** El servidor da las oportunidades ya sin duplicados, sin ocultos y con el límite del plan. */
    public static class Oportunidades extends OportunidadesService {
        private final ClienteApi api;

        public Oportunidades(CoincidenciaRepositorio coincidencias, ProcesoOcultoRepositorio ocultos, ClienteApi api) {
            super(coincidencias, ocultos);
            this.api = api;
        }

        @Override
        protected Base base(int userId, Plan plan, boolean incluirOcultas) {
            JsonNode r = api.llamar("oportunidades.vigentes", cuerpo("incluirOcultas", incluirOcultas));
            HashSet<String> ocultos = new HashSet<>();
            r.path("procesosOcultos").forEach(n -> ocultos.add(n.asText()));
            return new Base(co.secopradar.modelo.api.CoincidenciaApi.filas(r), r.path("ocultasPorPlan").asInt(),
                    r.path("ocultasPorUsuario").asInt(), ocultos);
        }
    }

    /** Subir un documento: pedir permiso, PUT del archivo a Storage y confirmar (el servidor lo revisa). */
    public static class Documentos extends DocumentosService {
        private final ClienteApi api;

        public Documentos(ProponenteRepositorio proponentes, DocumentoRepositorio documentos, ClienteApi api) {
            super(proponentes, documentos);
            this.api = api;
        }

        @Override
        public Documento subir(int usuarioId, TipoDocumento tipo, Path archivo, LocalDate vence) {
            byte[] datos = leer(archivo); // mismas revisiones que en JDBC antes de enviar nada
            String nombre = archivo.getFileName().toString();
            try {
                JsonNode p = api.llamar("documentos.pedirSubida", cuerpo("tipo", tipo.name(), "nombre", nombre,
                        "tamano", datos.length, "vence", vence == null ? null : vence.toString()));
                api.subir(p.path("url").asText(), datos, p.path("contentType").asText());
                JsonNode d = api.llamar("documentos.confirmarSubida", cuerpo("subidaId", p.path("subidaId").asText()));
                return ClienteApi.convertir(d, Documento.class);
            } catch (ErrorApi e) {
                throw new ErrorArchivo(e.getMessage());
            }
        }
    }

    /**
     * Postulaciones: cada cambio de etapa es una acción que el servidor
     * valida (orden, presupuesto, cierre) y guarda en una transacción.
     */
    public static class Postulaciones extends PostulacionesService {
        private final ClienteApi api;
        private final DocumentosService documentos;

        public Postulaciones(PostulacionRepositorio postulaciones, ProcesoRepositorio procesos, DocumentosService documentos, ClienteApi api) {
            super(postulaciones, procesos, documentos);
            this.documentos = documentos;
            this.api = api;
        }

        private Detalle accion(int id, int usuarioId, Object... datos) {
            Object[] todo = new Object[datos.length + 2];
            todo[0] = "id";
            todo[1] = id;
            System.arraycopy(datos, 0, todo, 2, datos.length);
            try {
                JsonNode r = api.llamar("postulaciones.actualizar", cuerpo(todo));
                Postulacion p = ClienteApi.convertir(r.path("postulacion"), Postulacion.class);
                return new Detalle(p, new co.secopradar.modelo.Avance(p, documentos.preparacion(usuarioId)));
            } catch (ErrorApi e) {
                throw new ErrorPostulacion(e.getMessage());
            }
        }

        @Override
        public int postularse(int usuarioId, String procesoId) {
            try {
                return api.llamar("postulaciones.crear", cuerpo("procesoId", procesoId)).path("id").asInt();
            } catch (ErrorApi e) {
                throw new ErrorPostulacion(e.getMessage());
            }
        }

        @Override
        public Detalle marcarRegistro(int id, int usuarioId) {
            return accion(id, usuarioId, "accion", "registro");
        }

        @Override
        public Detalle marcarRevisado(int id, int usuarioId, boolean revisado) {
            return accion(id, usuarioId, "accion", "revisar", "valor", revisado);
        }

        @Override
        public Detalle marcarPliego(int id, int usuarioId, boolean leido, String notas) {
            return accion(id, usuarioId, "accion", "pliego", "valor", leido, "notas", notas);
        }

        @Override
        public Detalle guardarNotas(int id, int usuarioId, String notas) {
            return accion(id, usuarioId, "accion", "notas", "notas", notas);
        }

        @Override
        public Detalle agregarRequisito(int id, int usuarioId, String texto) {
            return accion(id, usuarioId, "accion", "requisitoAgregar", "texto", texto);
        }

        @Override
        public Detalle alternarRequisito(int id, int usuarioId, int indice) {
            return accion(id, usuarioId, "accion", "requisitoAlternar", "indice", indice);
        }

        @Override
        public Detalle quitarRequisito(int id, int usuarioId, int indice) {
            return accion(id, usuarioId, "accion", "requisitoQuitar", "indice", indice);
        }

        @Override
        public Detalle guardarPropuesta(int id, int usuarioId, Long valor) {
            return accion(id, usuarioId, "accion", "propuesta", "valor", valor);
        }

        @Override
        public Detalle marcarPresentada(int id, int usuarioId, boolean presentada) {
            return accion(id, usuarioId, "accion", "presentar", "valor", presentada);
        }

        @Override
        public Detalle guardarResultado(int id, int usuarioId, Postulacion.Resultado r, LocalDate plazo) {
            return accion(id, usuarioId, "accion", "resultado", "resultado", r == null ? null : r.name(),
                    "plazo", plazo == null ? null : plazo.toString());
        }

        @Override
        public Detalle desistir(int id, int usuarioId, boolean desistir) {
            return accion(id, usuarioId, "accion", "desistir", "valor", desistir);
        }

        @Override
        public Detalle subirArchivoPropuesta(int id, int usuarioId, Path archivo) {
            byte[] datos = DocumentosService.leer(archivo);
            try {
                JsonNode p = api.llamar("postulaciones.pedirSubidaPropuesta", cuerpo("id", id,
                        "nombre", archivo.getFileName().toString(), "tamano", datos.length));
                api.subir(p.path("url").asText(), datos, p.path("contentType").asText());
                JsonNode r = api.llamar("postulaciones.confirmarSubidaPropuesta", cuerpo("subidaId", p.path("subidaId").asText()));
                Postulacion post = ClienteApi.convertir(r.path("postulacion"), Postulacion.class);
                return new Detalle(post, new co.secopradar.modelo.Avance(post, documentos.preparacion(usuarioId)));
            } catch (ErrorApi e) {
                throw new ErrorPostulacion(e.getMessage());
            }
        }
    }

    /**
     * La actualización diaria la hace el servidor (pg_cron). "Actualizar
     * ahora" en Administración le pide al servidor que la empiece.
     */
    public static class Actualizacion extends CronService {
        private final ClienteApi api;

        public Actualizacion(ClienteApi api, int horaDiaria) {
            super(null, null, null, null, null, 0, horaDiaria);
            this.api = api;
            avisar("La actualización la hace el servidor todos los días a las " + horaDiaria + ":00");
        }

        @Override
        public void iniciar(boolean descargarSiVacia, int diasPrimeraVez) {
        }

        @Override
        public boolean correrEnSegundoPlano(int dias) {
            avisar("Pidiendo al servidor que actualice...");
            CompletableFuture.runAsync(() -> {
                try {
                    avisar(api.llamar("admin.actualizarAhora", cuerpo("dias", dias)).path("mensaje")
                            .asText("Actualización iniciada en el servidor.") + " Revisa el historial en unos minutos.");
                } catch (ErrorApi e) {
                    avisar("No se pudo iniciar la actualización: " + e.getMessage());
                }
            });
            return true;
        }
    }

}
