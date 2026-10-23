package co.secopradar;

import co.secopradar.controlador.Navegador;
import co.secopradar.modelo.servicio.Servicios;
import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * SECOP RADAR: licitaciones del Estado colombiano (SECOP II) filtradas
 * para cada persona. Aplicación de escritorio JavaFX organizada en
 * Modelo (co.secopradar.modelo), Vista (resources/co/secopradar/vista/*.fxml)
 * y Controlador (co.secopradar.controlador).
 */
public class App extends Application {

    private Servicios servicios;

    @Override
    public void start(Stage ventana) {
        try {
            servicios = Servicios.iniciar();
        } catch (RuntimeException e) {
            Alert a = new Alert(Alert.AlertType.ERROR, e.getMessage());
            a.setHeaderText("No se pudo iniciar SECOP RADAR");
            a.showAndWait();
            return;
        }

        ventana.setTitle("SECOP RADAR");
        ventana.getIcons().add(new Image(App.class.getResourceAsStream("/co/secopradar/vista/img/logo-secop-radar-120.png")));
        ventana.setMinWidth(980);
        ventana.setMinHeight(640);
        Navegador.iniciar(ventana, getHostServices());
        Navegador.irALogin();
        ventana.show();

        // Primera vez: descarga en segundo plano los últimos días de SECOP II.
        servicios.actualizacion.iniciar(servicios.booleano("ingesta.al-iniciar", true),
                servicios.entero("ingesta.dias-primera-vez", 7));
    }

    @Override
    public void stop() {
        if (servicios != null) servicios.cerrar();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
