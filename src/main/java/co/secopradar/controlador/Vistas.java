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

    /** Pinta la lista de reglas de la contraseña (✓ verde / • gris) y la barra de fuerza. */
    static void pintarFuerza(co.secopradar.modelo.servicio.ValidadorPassword.Resultado r, javafx.scene.layout.Pane reglas,
                             javafx.scene.control.ProgressBar barra, Label nivel, boolean vacia) {
        reglas.getChildren().clear();
        for (var regla : r.reglas()) {
            Label l = new Label((regla.cumple() ? "✓ " : "• ") + regla.descripcion());
            l.getStyleClass().addAll("texto-chico", regla.cumple() ? "regla-ok" : "regla-falta");
            l.setMinWidth(190);
            reglas.getChildren().add(l);
        }
        barra.setProgress(vacia ? 0 : r.progreso());
        String texto = vacia ? "" : switch (r.nivel()) {
            case MUY_DEBIL -> "Muy débil";
            case DEBIL -> "Débil";
            case MEDIA -> "Aceptable";
            case FUERTE -> "Fuerte";
        };
        nivel.setText(texto);
        for (String c : java.util.List.of("fuerza-muy-debil", "fuerza-debil", "fuerza-media", "fuerza-fuerte")) {
            barra.getStyleClass().remove(c);
            nivel.getStyleClass().remove(c);
        }
        if (!vacia) {
            String c = "fuerza-" + r.nivel().name().toLowerCase().replace('_', '-');
            barra.getStyleClass().add(c);
            nivel.getStyleClass().add(c);
        }
    }

    /**
     * Avatar redondo: la foto de perfil o, si no hay (o no se puede leer), las
     * iniciales del nombre (o del correo) sobre un círculo de color.
     */
    static void pintarAvatar(javafx.scene.layout.StackPane caja, byte[] foto, String nombre, String email, double lado) {
        caja.getChildren().clear();
        caja.setMinSize(lado, lado);
        caja.setPrefSize(lado, lado);
        caja.setMaxSize(lado, lado);
        clase(caja, "avatar", true);
        if (foto != null && foto.length > 0) {
            // Se decodifica al doble del tamaño para que se vea nítida en pantallas con escala.
            javafx.scene.image.Image img = new javafx.scene.image.Image(new java.io.ByteArrayInputStream(foto),
                    lado * 2, lado * 2, true, true);
            if (!img.isError()) {
                javafx.scene.image.ImageView vista = new javafx.scene.image.ImageView(img);
                vista.setFitWidth(lado);
                vista.setFitHeight(lado);
                vista.setSmooth(true);
                vista.setClip(new javafx.scene.shape.Circle(lado / 2, lado / 2, lado / 2));
                clase(caja, "avatar-sin-foto", false);
                caja.getChildren().add(vista);
                return;
            }
        }
        Label iniciales = new Label(iniciales(nombre, email));
        iniciales.getStyleClass().add("avatar-iniciales");
        iniciales.setStyle("-fx-font-size: " + Math.round(lado * 0.38) + "px;");
        clase(caja, "avatar-sin-foto", true);
        caja.getChildren().add(iniciales);
    }

    /** "Ana María Pérez" -> "AP"; sin nombre, la primera letra del correo. */
    static String iniciales(String nombre, String email) {
        if (nombre != null && !nombre.isBlank()) {
            String[] partes = nombre.trim().split("\\s+");
            String r = partes[0].substring(0, 1) + (partes.length > 1 ? partes[partes.length - 1].substring(0, 1) : "");
            return r.toUpperCase();
        }
        return email == null || email.isBlank() ? "?" : email.substring(0, 1).toUpperCase();
    }

    /** Agrega o quita una clase CSS. */
    static void clase(Node n, String clase, boolean activa) {
        n.getStyleClass().remove(clase);
        if (activa) n.getStyleClass().add(clase);
    }
}
