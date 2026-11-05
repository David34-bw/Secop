package co.secopradar.controlador;

import co.secopradar.modelo.Oportunidad;
import co.secopradar.modelo.Preparacion;
import co.secopradar.modelo.Sesion;
import co.secopradar.modelo.servicio.PostulacionesService;
import co.secopradar.modelo.servicio.Servicios;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

import java.util.Map;
import java.util.Set;

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
     * Qué hacer al elegir Ocultar o Volver a mostrar en el menú. Lo pone la
     * pantalla que lo permite (Oportunidades); null = sin esas opciones.
     */
    public interface AccionesOcultar {
        void ocultar(Oportunidad o);

        void mostrar(Oportunidad o);
    }

    /**
     * Lo que la tarjeta necesita saber del usuario, calculado una vez por lista:
     * sus documentos, los procesos a los que ya se postuló (proceso -> postulación)
     * y los que ocultó.
     */
    public record Contexto(Preparacion preparacion, Map<String, Integer> postulaciones, Set<String> ocultos,
                           AccionesOcultar acciones) {
        public static Contexto delUsuario() {
            Servicios s = Servicios.get();
            int u = Sesion.usuarioId();
            return new Contexto(s.documentos.preparacion(u), s.postulaciones.idsPorProceso(u), Set.of(), null);
        }

        public Contexto conOcultar(Set<String> ocultos, AccionesOcultar acciones) {
            return new Contexto(preparacion, postulaciones, ocultos, acciones);
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
        v.controlador().armarMenu(o, ctx.ocultos().contains(o.proceso().id()), ctx.acciones());
        return v.raiz();
    }

    /** Menú del clic derecho: abrir, copiar el enlace y ocultar / volver a mostrar. */
    private void armarMenu(Oportunidad o, boolean oculta, AccionesOcultar acciones) {
        boolean sinUrl = o.url() == null || o.url().isBlank();
        MenuItem abrir = new MenuItem("Abrir en SECOP II");
        abrir.setOnAction(e -> Navegador.abrirEnlace(o.url()));
        abrir.setDisable(sinUrl);
        MenuItem copiar = new MenuItem("Copiar enlace");
        copiar.setOnAction(e -> {
            ClipboardContent c = new ClipboardContent();
            c.putString(o.url());
            Clipboard.getSystemClipboard().setContent(c);
        });
        copiar.setDisable(sinUrl);
        ContextMenu menu = new ContextMenu(abrir, copiar);
        if (acciones != null) {
            MenuItem ocultar = new MenuItem(oculta ? "Volver a mostrar" : "Ocultar oportunidad");
            ocultar.setOnAction(e -> {
                if (oculta) acciones.mostrar(o);
                else acciones.ocultar(o);
            });
            menu.getItems().addAll(new SeparatorMenuItem(), ocultar);
        }
        tarjeta.setOnContextMenuRequested(e -> menu.show(tarjeta, e.getScreenX(), e.getScreenY()));
        if (oculta) {
            tarjeta.getStyleClass().add("tarjeta-oculta");
            perfil.setText("Oculta" + (perfil.getText().isEmpty() ? "" : " · " + perfil.getText()));
        }
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
