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

    private final Servicios s = Servicios.get();
    private Usuario usuario;

    @FXML
    private void initialize() {
        Vistas.ocultar(mensaje);
        Vistas.ocultar(error);
        Vistas.ocultar(errorNombre);
        pintar();
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
