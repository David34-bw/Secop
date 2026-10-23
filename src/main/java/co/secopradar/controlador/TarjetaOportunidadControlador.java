package co.secopradar.controlador;

import co.secopradar.modelo.Oportunidad;
import javafx.fxml.FXML;
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
    @FXML private Hyperlink enlace;

    private String url;

    /** Crea una tarjeta lista para agregar a la lista. */
    public static Parent crear(Oportunidad o, boolean mostrarPerfil) {
        Navegador.Vista<TarjetaOportunidadControlador> v = Navegador.cargar("tarjetaOportunidad.fxml");
        v.controlador().mostrar(o, mostrarPerfil);
        return v.raiz();
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
