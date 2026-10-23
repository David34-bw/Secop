package co.secopradar.controlador;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Lista de textos que se escriben en un TextField (Enter o coma para
 * agregar) y se muestran como chips con una × para quitarlos.
 */
final class ListaChips {

    private final TextField entrada;
    private final FlowPane chips;
    private final Runnable alCambiar;
    private final List<String> valores = new ArrayList<>();

    ListaChips(TextField entrada, FlowPane chips, Runnable alCambiar) {
        this.entrada = entrada;
        this.chips = chips;
        this.alCambiar = alCambiar;
        entrada.setOnAction(e -> agregarEscrito());
        entrada.focusedProperty().addListener((o, antes, ahora) -> {
            if (!ahora) agregarEscrito();
        });
    }

    private void agregarEscrito() {
        boolean cambio = false;
        for (String t : entrada.getText().split(",")) {
            String s = t.trim();
            if (!s.isEmpty() && !valores.contains(s)) {
                valores.add(s);
                cambio = true;
            }
        }
        entrada.clear();
        if (cambio) {
            pintar();
            alCambiar.run();
        }
    }

    void reemplazar(List<String> nuevos) {
        valores.clear();
        valores.addAll(nuevos);
        pintar();
    }

    List<String> valores() {
        return List.copyOf(valores);
    }

    private void pintar() {
        chips.getChildren().clear();
        for (String v : valores) {
            Label texto = new Label(v);
            Button quitar = new Button("×");
            quitar.getStyleClass().add("chip-quitar");
            quitar.setOnAction(e -> {
                valores.remove(v);
                pintar();
                alCambiar.run();
            });
            HBox chip = new HBox(4, texto, quitar);
            chip.getStyleClass().addAll("chip", "chip-activo");
            chips.getChildren().add(chip);
        }
    }
}
