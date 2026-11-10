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
    @FXML private Button botonPostulaciones;
    @FXML private Button botonDocumentos;
    @FXML private Button botonBuscar;
    @FXML private Button botonPlanes;
    @FXML private Button botonCuenta;
    @FXML private Button botonAdmin;
    @FXML private Button botonRefrescar;
    @FXML private StackPane avatar;

    private final Servicios s = Servicios.get();
    private Map<Pantalla, Button> botones;
    private Pantalla actual = Pantalla.OPORTUNIDADES;

    @FXML
    private void initialize() {
        Usuario u = usuario();
        email.setText(u.email());
        Vistas.mostrar(botonAdmin, u.esAdmin());
        botones = Map.ofEntries(Map.entry(Pantalla.INICIO, botonInicio), Map.entry(Pantalla.OPORTUNIDADES, botonOportunidades),
                Map.entry(Pantalla.PERFILES, botonPerfiles), Map.entry(Pantalla.NUEVO_PERFIL, botonPerfiles),
                Map.entry(Pantalla.POSTULACIONES, botonPostulaciones), Map.entry(Pantalla.POSTULACION, botonPostulaciones),
                Map.entry(Pantalla.DOCUMENTOS, botonDocumentos), Map.entry(Pantalla.BUSCAR, botonBuscar),
                Map.entry(Pantalla.PLANES, botonPlanes), Map.entry(Pantalla.CUENTA, botonCuenta), Map.entry(Pantalla.ADMIN, botonAdmin));

        // Modo API: lo que usan las demás pestañas se pide ya, en segundo plano.
        s.precargar();
        Vistas.mostrar(botonRefrescar, s.api != null);
        pintarFoto();

        // El modelo avisa desde su hilo; la vista se actualiza en el hilo de JavaFX.
        pintarEstado(s.actualizacion.ultimoResultado());
        s.actualizacion.alCambiarEstado("principal", m -> Platform.runLater(() -> pintarEstado(m)));
    }

    private void pintarEstado(String mensaje) {
        boolean corriendo = s.actualizacion.estaCorriendo();
        String base = s.enLaNube() ? "Supabase" : "base local";
        estado.setText(corriendo ? mensaje : mensaje + "  ·  " + s.procesos.contar() + " procesos en " + base
                + (s.avisoConexion != null ? "  ·  ⚠ " + s.avisoConexion : ""));
        Vistas.clase(indicador, "corriendo", corriendo);
        Vistas.clase(indicador, "sin-nube", !corriendo && s.avisoConexion != null);
    }

    /**
     * Avatar del menú: primero las iniciales (al instante) y la foto cuando
     * llega, en segundo plano, para no demorar la entrada.
     */
    public void pintarFoto() {
        Usuario u = usuario();
        Vistas.pintarAvatar(avatar, null, u.nombre(), u.email(), 32);
        int id = u.id();
        java.util.concurrent.CompletableFuture.supplyAsync(() -> s.fotos.de(id).orElse(null))
                .whenComplete((foto, e) -> {
                    if (e == null && foto != null) {
                        Platform.runLater(() -> {
                            // Si mientras tanto cerró sesión o entró otro usuario, no se pinta.
                            if (Sesion.usuarioId() != null && Sesion.usuarioId() == id) {
                                Vistas.pintarAvatar(avatar, foto, u.nombre(), u.email(), 32);
                            }
                        });
                    }
                });
    }

    @FXML
    private void irACuentaDesdeAvatar() {
        mostrar(Pantalla.CUENTA);
    }

    private Usuario usuario() {
        return s.usuarios.porId(Sesion.usuarioId()).orElseThrow();
    }

    /** Carga la vista en el centro y devuelve su controlador. */
    public <C> C mostrar(Pantalla pantalla) {
        // Administración solo para administradores (el botón ya está oculto para los demás).
        if (pantalla == Pantalla.ADMIN && !usuario().esAdmin()) pantalla = Pantalla.OPORTUNIDADES;
        actual = pantalla;
        Navegador.Vista<C> v = Navegador.cargar(pantalla.fxml);
        contenido.getChildren().setAll(v.raiz());
        botones.values().forEach(b -> Vistas.clase(b, "activo", false));
        Vistas.clase(botones.get(pantalla), "activo", true);
        Navegador.ventana().setTitle("SECOP RADAR — " + pantalla.titulo);
        return v.controlador();
    }

    /** Olvida lo guardado en memoria y vuelve a cargar la pantalla actual con datos del servidor. */
    @FXML
    private void refrescarDatos() {
        s.refrescarDatos();
        // Las pantallas de detalle (nuevo perfil, una postulación) vuelven a su lista.
        Pantalla p = switch (actual) {
            case NUEVO_PERFIL -> Pantalla.PERFILES;
            case POSTULACION -> Pantalla.POSTULACIONES;
            default -> actual;
        };
        mostrar(p);
        pintarEstado(s.actualizacion.ultimoResultado());
    }

    @FXML private void irAInicio() { mostrar(Pantalla.INICIO); }

    @FXML private void irAOportunidades() { mostrar(Pantalla.OPORTUNIDADES); }

    @FXML private void irAPerfiles() { mostrar(Pantalla.PERFILES); }

    @FXML private void irAPostulaciones() { mostrar(Pantalla.POSTULACIONES); }

    @FXML private void irADocumentos() { mostrar(Pantalla.DOCUMENTOS); }

    @FXML private void irABuscar() { mostrar(Pantalla.BUSCAR); }

    @FXML private void irAPlanes() { mostrar(Pantalla.PLANES); }

    @FXML private void irACuenta() { mostrar(Pantalla.CUENTA); }

    @FXML private void irAAdmin() { mostrar(Pantalla.ADMIN); }

    @FXML
    private void salir() {
        s.salir();
        Navegador.irALogin();
    }
}
