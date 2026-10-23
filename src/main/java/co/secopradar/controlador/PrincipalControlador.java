package co.secopradar.controlador;

import co.secopradar.modelo.Sesion;
import co.secopradar.modelo.Usuario;
import co.secopradar.modelo.servicio.Servicios;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import java.util.Map;

/**
 * Controlador de principal.fxml: menú lateral, área central donde se
 * cargan las demás vistas y barra de estado de la actualización de datos.
 */
public class PrincipalControlador {

    @FXML private StackPane contenido;
    @FXML private Label email;
    @FXML private Label estado;
    @FXML private Label indicador;
    @FXML private Button botonInicio;
    @FXML private Button botonOportunidades;
    @FXML private Button botonPerfiles;
    @FXML private Button botonBuscar;
    @FXML private Button botonPlanes;
    @FXML private Button botonCuenta;
    @FXML private Button botonAdmin;

    private final Servicios s = Servicios.get();
    private Map<Pantalla, Button> botones;

    @FXML
    private void initialize() {
        Usuario u = usuario();
        email.setText(u.email());
        Vistas.mostrar(botonAdmin, u.esAdmin());
        botones = Map.of(Pantalla.INICIO, botonInicio, Pantalla.OPORTUNIDADES, botonOportunidades,
                Pantalla.PERFILES, botonPerfiles, Pantalla.NUEVO_PERFIL, botonPerfiles, Pantalla.BUSCAR, botonBuscar,
                Pantalla.PLANES, botonPlanes, Pantalla.CUENTA, botonCuenta, Pantalla.ADMIN, botonAdmin);

        // El modelo avisa desde su hilo; la vista se actualiza en el hilo de JavaFX.
        pintarEstado(s.actualizacion.ultimoResultado());
        s.actualizacion.alCambiarEstado("principal", m -> Platform.runLater(() -> pintarEstado(m)));
    }

    private void pintarEstado(String mensaje) {
        boolean corriendo = s.actualizacion.estaCorriendo();
        estado.setText(corriendo ? mensaje : mensaje + "  ·  " + s.procesos.contar() + " procesos en la base");
        Vistas.clase(indicador, "corriendo", corriendo);
    }

    private Usuario usuario() {
        return s.usuarios.porId(Sesion.usuarioId()).orElseThrow();
    }

    /** Carga la vista en el centro y devuelve su controlador. */
    public <C> C mostrar(Pantalla pantalla) {
        // Administración solo para administradores (el botón ya está oculto para los demás).
        if (pantalla == Pantalla.ADMIN && !usuario().esAdmin()) pantalla = Pantalla.OPORTUNIDADES;
        Navegador.Vista<C> v = Navegador.cargar(pantalla.fxml);
        contenido.getChildren().setAll(v.raiz());
        botones.values().forEach(b -> Vistas.clase(b, "activo", false));
        Vistas.clase(botones.get(pantalla), "activo", true);
        Navegador.ventana().setTitle("SECOP RADAR — " + pantalla.titulo);
        return v.controlador();
    }

    @FXML private void irAInicio() { mostrar(Pantalla.INICIO); }

    @FXML private void irAOportunidades() { mostrar(Pantalla.OPORTUNIDADES); }

    @FXML private void irAPerfiles() { mostrar(Pantalla.PERFILES); }

    @FXML private void irABuscar() { mostrar(Pantalla.BUSCAR); }

    @FXML private void irAPlanes() { mostrar(Pantalla.PLANES); }

    @FXML private void irACuenta() { mostrar(Pantalla.CUENTA); }

    @FXML private void irAAdmin() { mostrar(Pantalla.ADMIN); }

    @FXML
    private void salir() {
        Sesion.cerrar();
        Navegador.irALogin();
    }
}
