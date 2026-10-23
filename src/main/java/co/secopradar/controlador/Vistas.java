package co.secopradar.controlador;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;

import java.util.Optional;

/** Pequeñas ayudas de interfaz que comparten los controladores. */
final class Vistas {

    private Vistas() {
    }

    static void mostrar(Node n, boolean visible) {
        n.setVisible(visible);
        n.setManaged(visible);
    }

    static void mostrarError(Label etiqueta, String mensaje) {
        etiqueta.setText(mensaje == null ? "" : mensaje);
        mostrar(etiqueta, mensaje != null && !mensaje.isBlank());
    }

    static void ocultar(Label etiqueta) {
        mostrarError(etiqueta, null);
    }

    static boolean confirmar(String titulo, String mensaje) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, mensaje, ButtonType.OK, ButtonType.CANCEL);
        a.setHeaderText(titulo);
        a.initOwner(Navegador.ventana());
        Optional<ButtonType> r = a.showAndWait();
        return r.isPresent() && r.get() == ButtonType.OK;
    }

    static void informar(String titulo, String mensaje) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, mensaje);
        a.setHeaderText(titulo);
        a.initOwner(Navegador.ventana());
        a.showAndWait();
    }

    /** Agrega o quita una clase CSS. */
    static void clase(Node n, String clase, boolean activa) {
        n.getStyleClass().remove(clase);
        if (activa) n.getStyleClass().add(clase);
    }
}
