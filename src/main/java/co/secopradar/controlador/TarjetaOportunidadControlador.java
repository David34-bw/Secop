package co.secopradar.controlador;

import co.secopradar.modelo.Oportunidad;
import co.secopradar.modelo.Preparacion;
import co.secopradar.modelo.Sesion;
import co.secopradar.modelo.servicio.PostulacionesService;
import co.secopradar.modelo.servicio.Servicios;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.Parent;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

/** Controlador de tarjetaOportunidad.fxml: pinta un proceso (Oportunidad del modelo). */
public class TarjetaOportunidadControlador {

    @FXML private VBox tarjeta;
    @FXML private Label entidad;
    @FXML private Label objeto;
    @FXML private Label puntos;
    @FXML private Label avisoTemprano;
    @FXML private Label estado;
    @FXML private Label valor;
    @FXML private Label departamento;
    @FXML private Label modalidad;
    @FXML private Label cierre;
    @FXML private Label perfil;
    @FXML private Label documentos;
    @FXML private Button postularme;
    @FXML private Hyperlink enlace;

    private String url;
    private Oportunidad oportunidad;
    private Integer postulacionId;

    /**
     * Lo que la tarjeta necesita saber del usuario, calculado una vez por lista:
     * sus documentos y los procesos a los que ya se postuló (proceso -> postulación).
     */
    public record Contexto(Preparacion preparacion, java.util.Map<String, Integer> postulaciones) {
        public static Contexto delUsuario() {
            Servicios s = Servicios.get();
            int u = Sesion.usuarioId();
            return new Contexto(s.documentos.preparacion(u), s.postulaciones.idsPorProceso(u));
        }
    }

    /**
     * Crea una tarjeta lista para agregar a la lista. preparacion (puede ser
     * null): los documentos del usuario, para decirle si le falta algo para
     * la modalidad de este proceso.
     */
    public static Parent crear(Oportunidad o, boolean mostrarPerfil, Contexto ctx) {
        Navegador.Vista<TarjetaOportunidadControlador> v = Navegador.cargar("tarjetaOportunidad.fxml");
        v.controlador().mostrar(o, mostrarPerfil);
        v.controlador().mostrarDocumentos(o, ctx.preparacion());
        v.controlador().mostrarPostulacion(o, ctx.postulaciones().get(o.proceso().id()));
        return v.raiz();
    }

    private void mostrarPostulacion(Oportunidad o, Integer id) {
        oportunidad = o;
        postulacionId = id;
        Integer dias = o.dias();
        boolean abierto = !o.avisoTemprano() && (dias == null || dias >= 0);
        Vistas.mostrar(postularme, id != null || abierto);
        postularme.setText(id != null ? "Ver mi postulación" : "Postularme");
    }

    @FXML
    private void postularme() {
        try {
            int id = postulacionId != null ? postulacionId
                    : Servicios.get().postulaciones.postularse(Sesion.usuarioId(), oportunidad.proceso().id());
            PostulacionControlador.abrir(id);
        } catch (PostulacionesService.ErrorPostulacion e) {
            Vistas.informar("No puedes postularte", e.getMessage());
        }
    }

    private void mostrarDocumentos(Oportunidad o, Preparacion preparacion) {
        var estado = preparacion == null || o.avisoTemprano() ? java.util.Optional.<Preparacion.Estado>empty()
                : preparacion.paraModalidad(o.proceso().modalidad());
        Vistas.mostrar(documentos, estado.isPresent());
        estado.ifPresent(e -> {
            documentos.setText(e.listo() ? "✓ Tienes los documentos básicos para esta modalidad"
                    : "Para presentarte te falta: " + String.join(", ", e.faltantes()));
            documentos.getStyleClass().add(e.listo() ? "preparacion-lista" : "preparacion-falta");
        });
    }

    public void mostrar(Oportunidad o, boolean mostrarPerfil) {
        entidad.setText(o.entidad());
        objeto.setText(o.objeto());
        puntos.setText(o.score() + " pts");
        Vistas.mostrar(avisoTemprano, o.avisoTemprano());

        String clase = o.estado().clase();
        estado.setText("● " + o.estado().label());
        estado.getStyleClass().add(clase);
        tarjeta.getStyleClass().add("borde-" + clase);

        valor.setText(o.valor());
        departamento.setText(o.departamento() == null ? "Sin departamento" : o.departamento());
        modalidad.setText(o.modalidad());
        cierre.setText(o.textoCierre() == null ? "" : o.textoCierre());
        if (o.textoCierre() != null) cierre.setTooltip(new Tooltip("Fecha de cierre: " + o.fechaCierre()));

        url = o.url();
        Vistas.mostrar(enlace, url != null);
        perfil.setText(mostrarPerfil && o.perfil() != null ? "Perfil: " + o.perfil() : "");
    }

    @FXML
    private void abrirEnSecop() {
        Navegador.abrirEnlace(url);
    }
}
