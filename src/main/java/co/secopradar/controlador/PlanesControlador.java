package co.secopradar.controlador;

import co.secopradar.modelo.Plan;
import co.secopradar.modelo.Sesion;
import co.secopradar.modelo.servicio.Servicios;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Controlador de planes.fxml. */
public class PlanesControlador {

    @FXML private Label planActual;
    @FXML private HBox tarjetas;

    @FXML
    private void initialize() {
        Servicios s = Servicios.get();
        Plan actual = Plan.efectivo(s.usuarios.porId(Sesion.usuarioId()).orElseThrow());
        planActual.setText("prueba".equals(actual.origen())
                ? "Estás en la prueba del plan " + actual.nombre() + ": te quedan " + actual.diasRestantes()
                + (actual.diasRestantes() == 1 ? " día." : " días.")
                : "Tu plan actual: " + actual.nombre() + ".");

        for (Plan p : Plan.PLANES.values()) {
            VBox t = new VBox(8);
            t.setPadding(new Insets(22));
            t.getStyleClass().add("plan");
            if (p.id().equals(Plan.PLAN_DE_PRUEBA)) {
                t.getStyleClass().add("plan-destacado");
                Label insignia = new Label("Incluido en la prueba gratis");
                insignia.getStyleClass().add("plan-insignia");
                t.getChildren().add(insignia);
            }
            Label nombre = new Label(p.nombre());
            nombre.getStyleClass().add("subtitulo");
            Label precio = new Label(p.precioTexto() + " / mes");
            precio.getStyleClass().add("plan-precio");
            t.getChildren().addAll(nombre, precio);
            for (String b : p.beneficios()) {
                Label l = new Label((b.startsWith("Sin") ? "–  " : "✓  ") + b);
                l.getStyleClass().add(b.startsWith("Sin") ? "apagado" : "beneficio");
                t.getChildren().add(l);
            }
            if (p.id().equals(actual.id())) {
                Label tuyo = new Label("Tu plan actual");
                tuyo.getStyleClass().add("plan-actual");
                t.getChildren().add(tuyo);
            }
            HBox.setHgrow(t, Priority.ALWAYS);
            t.setMaxWidth(Double.MAX_VALUE);
            tarjetas.getChildren().add(t);
        }
    }
}
