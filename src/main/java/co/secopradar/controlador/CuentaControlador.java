package co.secopradar.controlador;

import co.secopradar.modelo.Plan;
import co.secopradar.modelo.Sesion;
import co.secopradar.modelo.Usuario;
import co.secopradar.modelo.servicio.CuentaService;
import co.secopradar.modelo.servicio.Servicios;
import co.secopradar.modelo.servicio.ValidadorPassword;
import co.secopradar.modelo.util.Formato;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.concurrent.Task;
import javafx.stage.FileChooser;
import co.secopradar.modelo.servicio.FotoPerfilService;
import co.secopradar.modelo.api.ClienteApi;
import java.io.File;

/** Controlador de cuenta.fxml: plan, avisos, nombre y contraseña. */
public class CuentaControlador {

    @FXML private Label mensaje;
    @FXML private Label error;
    @FXML private Label plan;
    @FXML private VBox beneficios;
    @FXML private CheckBox avisos;
    @FXML private Label datos;
    @FXML private TextField nombre;
    @FXML private PasswordField actual;
    @FXML private PasswordField nueva;
    @FXML private Label errorNombre;
    @FXML private ProgressBar barraFuerza;
    @FXML private Label nivelFuerza;
    @FXML private FlowPane reglasPassword;
    @FXML private StackPane avatar;
    @FXML private Button botonFoto;
    @FXML private Button botonQuitarFoto;
    @FXML private ProgressIndicator cargandoFoto;

    private final Servicios s = Servicios.get();
    private Usuario usuario;

    @FXML
    private void initialize() {
        Vistas.ocultar(mensaje);
        Vistas.ocultar(error);
        Vistas.ocultar(errorNombre);
        pintar();
        Vistas.mostrar(cargandoFoto, false);
        cargarFoto();
        nombre.textProperty().addListener((o, a, b) -> {
            Vistas.ocultar(errorNombre);
            Vistas.clase(nombre, "campo-error", false);
        });
        nueva.textProperty().addListener((o, a, b) -> pintarFuerza());
        pintarFuerza();
    }

    private ValidadorPassword.Resultado pintarFuerza() {
        ValidadorPassword.Resultado r = ValidadorPassword.evaluar(nueva.getText(), usuario.email(), usuario.nombre());
        Vistas.pintarFuerza(r, reglasPassword, barraFuerza, nivelFuerza, nueva.getText().isEmpty());
        return r;
    }

    private void pintar() {
        usuario = s.usuarios.porId(Sesion.usuarioId()).orElseThrow();
        Plan p = Plan.efectivo(usuario);
        String hasta = Formato.fechaLarga(p.hasta());
        plan.setText(p.nombre() + switch (p.origen()) {
            case "prueba" -> " — prueba gratis hasta el " + hasta + " (" + p.diasRestantes() + " días)";
            case "prepago" -> " — pagado hasta el " + hasta;
            case "gracia" -> " — cobro pendiente, se conserva hasta el " + hasta;
            case "admin" -> " — cuenta de administrador, sin límites";
            default -> "";
        });
        beneficios.getChildren().clear();
        for (String b : p.beneficios()) {
            Label l = new Label((b.startsWith("Sin") ? "–  " : "✓  ") + b);
            l.getStyleClass().add(b.startsWith("Sin") ? "apagado" : "beneficio");
            beneficios.getChildren().add(l);
        }
        avisos.setSelected(usuario.alertasActivas());
        datos.setText("Correo: " + usuario.email() + " · Cuenta creada el " + Formato.fechaLarga(usuario.createdAt()));
        nombre.setText(usuario.nombre() == null ? "" : usuario.nombre());
    }

    // ---- Foto de perfil ----

    private void pintarFoto(byte[] foto) {
        Vistas.pintarAvatar(avatar, foto, usuario.nombre(), usuario.email(), 96);
        botonFoto.setText(foto == null ? "Subir foto" : "Cambiar foto");
        Vistas.mostrar(botonQuitarFoto, foto != null);
    }

    /** Iniciales al instante y la foto cuando llega (normalmente ya está en memoria). */
    private void cargarFoto() {
        pintarFoto(null);
        enSegundoPlano(() -> s.fotos.de(usuario.id()).orElse(null), this::pintarFoto, null);
    }

    @FXML
    private void elegirFoto() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Elegir foto de perfil");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes JPG o PNG", "*.jpg", "*.jpeg", "*.png",
                "*.JPG", "*.JPEG", "*.PNG"));
        File f = fc.showOpenDialog(Navegador.ventana());
        if (f == null) return;
        int id = usuario.id();
        enSegundoPlano(() -> {
            s.fotos.cambiar(id, f.toPath());
            return s.fotos.de(id).orElse(null);
        }, foto -> {
            pintarFoto(foto);
            exito("Foto de perfil actualizada.");
            Navegador.perfilCambiado();
        }, "No se pudo guardar la foto: ");
    }

    @FXML
    private void quitarFoto() {
        if (!Vistas.confirmar("Quitar foto", "¿Quitar tu foto de perfil? Se mostrarán tus iniciales.")) return;
        int id = usuario.id();
        enSegundoPlano(() -> {
            s.fotos.quitar(id);
            return null;
        }, foto -> {
            pintarFoto(null);
            exito("Foto de perfil quitada.");
            Navegador.perfilCambiado();
        }, "No se pudo quitar la foto: ");
    }

    /**
     * Trabajo con red o con la imagen fuera del hilo de la pantalla. Mientras
     * corre, los botones de la foto quedan desactivados (no hay dos subidas a la vez).
     * prefijoError null: un fallo solo deja las iniciales (al cargar).
     */
    private void enSegundoPlano(java.util.concurrent.Callable<byte[]> trabajo, java.util.function.Consumer<byte[]> listo,
                                String prefijoError) {
        Task<byte[]> t = new Task<>() {
            @Override
            protected byte[] call() throws Exception {
                return trabajo.call();
            }
        };
        botonFoto.setDisable(true);
        botonQuitarFoto.setDisable(true);
        Vistas.mostrar(cargandoFoto, true);
        Runnable fin = () -> {
            botonFoto.setDisable(false);
            botonQuitarFoto.setDisable(false);
            Vistas.mostrar(cargandoFoto, false);
        };
        t.setOnSucceeded(e -> {
            fin.run();
            listo.accept(t.getValue());
        });
        t.setOnFailed(e -> {
            fin.run();
            if (prefijoError == null) return;
            Throwable x = t.getException();
            String m = x instanceof FotoPerfilService.ErrorFoto || x instanceof ClienteApi.ErrorApi
                    ? x.getMessage() : prefijoError + x.getMessage();
            Vistas.ocultar(mensaje);
            Vistas.mostrarError(error, m);
        });
        Thread hilo = new Thread(t, "foto-perfil");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void exito(String texto) {
        Vistas.ocultar(error);
        Vistas.mostrarError(mensaje, texto);
    }

    @FXML
    private void cambiarAvisos() {
        s.usuarios.cambiarAvisos(usuario.id(), avisos.isSelected());
        exito(avisos.isSelected() ? "Avisos de oportunidades nuevas activados." : "Avisos de oportunidades nuevas desactivados.");
    }

    @FXML
    private void guardarNombre() {
        try {
            String guardado = s.cuentas.cambiarNombre(usuario, nombre.getText());
            exito("Nombre actualizado: " + guardado + ".");
            pintar();
            cargarFoto(); // las iniciales cambian con el nombre
            Navegador.perfilCambiado();
        } catch (CuentaService.ErrorCuenta e) {
            Vistas.mostrarError(errorNombre, e.getMessage());
            Vistas.clase(nombre, "campo-error", true);
        }
    }

    @FXML
    private void cambiarPassword() {
        try {
            s.cuentas.cambiarPassword(usuario, actual.getText(), nueva.getText());
            actual.clear();
            nueva.clear();
            pintarFuerza();
            exito("Contraseña actualizada.");
        } catch (CuentaService.ErrorCuenta e) {
            Vistas.ocultar(mensaje);
            Vistas.mostrarError(error, e.getMessage());
        }
    }

    @FXML
    private void verPlanes() {
        Navegador.mostrar(Pantalla.PLANES);
    }

    @FXML
    private void verTerminos() {
        Navegador.mostrarDocumentoLegal("terminos");
    }

    @FXML
    private void verPrivacidad() {
        Navegador.mostrarDocumentoLegal("privacidad");
    }
}
