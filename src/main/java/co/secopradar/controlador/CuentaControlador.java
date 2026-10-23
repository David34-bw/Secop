package co.secopradar.controlador;

import co.secopradar.modelo.Plan;
import co.secopradar.modelo.Sesion;
import co.secopradar.modelo.Usuario;
import co.secopradar.modelo.servicio.CuentaService;
import co.secopradar.modelo.servicio.Servicios;
import co.secopradar.modelo.util.Formato;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
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

    private final Servicios s = Servicios.get();
    private Usuario usuario;

    @FXML
    private void initialize() {
        Vistas.ocultar(mensaje);
        Vistas.ocultar(error);
        pintar();
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
        String limpio = nombre.getText().trim();
        s.usuarios.cambiarNombre(usuario.id(), limpio.isEmpty() ? null : limpio.substring(0, Math.min(200, limpio.length())));
        exito("Nombre actualizado.");
        pintar();
    }

    @FXML
    private void cambiarPassword() {
        try {
            s.cuentas.cambiarPassword(usuario, actual.getText(), nueva.getText());
            actual.clear();
            nueva.clear();
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
