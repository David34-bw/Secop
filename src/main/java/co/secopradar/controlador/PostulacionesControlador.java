package co.secopradar.controlador;

import co.secopradar.modelo.Avance;
import co.secopradar.modelo.Postulacion;
import co.secopradar.modelo.Sesion;
import co.secopradar.modelo.servicio.PostulacionesService;
import co.secopradar.modelo.servicio.Servicios;
import co.secopradar.modelo.util.Formato;
import co.secopradar.modelo.util.TextoOracion;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/** Controlador de postulaciones.fxml: lista de procesos a los que el usuario se postuló, con su avance. */
public class PostulacionesControlador {

    @FXML private VBox vacio;
    @FXML private VBox lista;

    private final Servicios s = Servicios.get();

    @FXML
    private void initialize() {
        List<PostulacionesService.Detalle> l = s.postulaciones.lista(Sesion.usuarioId());
        Vistas.mostrar(vacio, l.isEmpty());
        for (var d : l) lista.getChildren().add(tarjeta(d));
    }

    private Node tarjeta(PostulacionesService.Detalle d) {
        Postulacion p = d.postulacion();
        Avance a = d.avance();
        Label entidad = new Label(TextoOracion.aTitulo(p.entidad()));
        entidad.getStyleClass().add("texto-chico");
        String objeto = TextoOracion.aOracion(p.objeto());
        if (objeto != null && objeto.length() > 180) objeto = objeto.substring(0, 177) + "...";
        Label titulo = new Label(objeto == null ? "Sin descripción" : objeto);
        titulo.getStyleClass().add("tarjeta-titulo");
        titulo.setWrapText(true);

        ProgressBar barra = new ProgressBar(a.progreso());
        barra.getStyleClass().add("barra-avance");
        barra.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(barra, Priority.ALWAYS);
        Label resumen = new Label(a.resumen());
        resumen.getStyleClass().add("resultado-frase");
        resumen.setMinWidth(Label.USE_PREF_SIZE);
        HBox avance = new HBox(10, barra, resumen);
        avance.setAlignment(Pos.CENTER_LEFT);
        avance.setMaxWidth(640);

        Integer dias = Formato.diasRestantes(p.fechaCierre());
        String cierre = p.presentadaAt() != null ? "Oferta presentada el " + Formato.fechaLarga(p.presentadaAt())
                : dias == null ? "Sin fecha de cierre" : dias < 0 ? "Cerró el " + Formato.fechaLarga(p.fechaCierre())
                : Formato.textoCierre(dias) + " · " + Formato.fechaHora(p.fechaCierre());
        Label datos = new Label(Formato.pesos(p.valor()) + "  ·  " + p.modalidad() + "  ·  " + cierre);
        datos.getStyleClass().add("dato");
        datos.setWrapText(true);
        if (!p.terminada() && p.presentadaAt() == null && dias != null && dias >= 0 && dias <= 3) {
            datos.getStyleClass().add("urgente-texto");
        }

        Button abrir = new Button(p.terminada() ? "Ver" : "Continuar");
        abrir.getStyleClass().add(p.terminada() ? "boton-borde" : "boton-azul");
        abrir.setOnAction(e -> PostulacionControlador.abrir(p.id()));
        HBox pie = new HBox(abrir);
        pie.setAlignment(Pos.CENTER_RIGHT);

        VBox tarjeta = new VBox(6, entidad, titulo, datos, avance, pie);
        tarjeta.getStyleClass().add("tarjeta-oportunidad");
        if (p.terminada()) tarjeta.getStyleClass().add("inactivo");
        tarjeta.setPadding(new Insets(14, 18, 14, 18));
        return tarjeta;
    }

    @FXML
    private void verOportunidades() {
        Navegador.mostrar(Pantalla.OPORTUNIDADES);
    }
}
