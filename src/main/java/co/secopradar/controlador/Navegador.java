package co.secopradar.controlador;

import javafx.application.HostServices;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;

/**
 * Carga las vistas FXML (resources/co/secopradar/vista) y cambia de
 * pantalla. Las vistas de la app (Oportunidades, Mis búsquedas...) se
 * muestran en el centro de la ventana principal.
 */
public final class Navegador {

    private static final String CARPETA_VISTAS = "/co/secopradar/vista/";
    private static final String HOJA_ESTILOS = CARPETA_VISTAS + "estilos.css";

    private static Stage ventana;
    private static HostServices host;
    private static PrincipalControlador principal;

    /** Una vista cargada: su nodo raíz y su controlador. */
    public record Vista<C>(Parent raiz, C controlador) {
    }

    private Navegador() {
    }

    public static void iniciar(Stage stage, HostServices hostServices) {
        ventana = stage;
        host = hostServices;
    }

    public static <C> Vista<C> cargar(String archivoFxml) {
        URL url = Navegador.class.getResource(CARPETA_VISTAS + archivoFxml);
        if (url == null) throw new IllegalStateException("No existe la vista " + archivoFxml);
        try {
            FXMLLoader loader = new FXMLLoader(url);
            Parent raiz = loader.load();
            return new Vista<>(raiz, loader.getController());
        } catch (IOException e) {
            throw new UncheckedIOException("Error cargando " + archivoFxml + ": " + e.getMessage(), e);
        }
    }

    private static void cambiarEscena(Parent raiz, double ancho, double alto) {
        Scene escena = ventana.getScene();
        if (escena == null) {
            escena = new Scene(raiz, ancho, alto);
            escena.getStylesheets().add(Navegador.class.getResource(HOJA_ESTILOS).toExternalForm());
            ventana.setScene(escena);
        } else {
            escena.setRoot(raiz);
        }
    }

    public static void irALogin() {
        principal = null;
        cambiarEscena(cargar("login.fxml").raiz(), 1180, 760);
    }

    public static void irARegistro() {
        cambiarEscena(cargar("registro.fxml").raiz(), 1180, 760);
    }

    public static void irAPrincipal() {
        Vista<PrincipalControlador> v = cargar("principal.fxml");
        principal = v.controlador();
        cambiarEscena(v.raiz(), 1180, 760);
        principal.mostrar(Pantalla.OPORTUNIDADES);
    }

    /** Cambia la pantalla del centro de la ventana principal. */
    public static <C> C mostrar(Pantalla pantalla) {
        if (principal == null) throw new IllegalStateException("No hay sesión iniciada");
        return principal.mostrar(pantalla);
    }

    /** Abre un enlace (por ejemplo el proceso en SECOP) en el navegador del sistema. */
    public static void abrirEnlace(String url) {
        if (url != null && !url.isBlank()) host.showDocument(url);
    }

    /** Muestra Términos o Política de datos en una ventana aparte. */
    public static void mostrarDocumentoLegal(String documento) {
        Vista<LegalControlador> v = cargar("legal.fxml");
        v.controlador().mostrar(documento);
        Stage dialogo = new Stage();
        dialogo.initOwner(ventana);
        dialogo.initModality(Modality.WINDOW_MODAL);
        dialogo.setTitle(v.controlador().titulo());
        Scene escena = new Scene(v.raiz(), 760, 640);
        escena.getStylesheets().add(Navegador.class.getResource(HOJA_ESTILOS).toExternalForm());
        dialogo.setScene(escena);
        dialogo.show();
    }

    public static Stage ventana() {
        return ventana;
    }
}
