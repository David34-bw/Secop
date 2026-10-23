package co.secopradar.controlador;

import co.secopradar.modelo.Sesion;
import co.secopradar.modelo.Usuario;
import co.secopradar.modelo.servicio.CuentaService;
import co.secopradar.modelo.servicio.Servicios;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Controlador de registro.fxml. El correo se valida al salir del campo:
 * primero el formato (al instante) y luego, en segundo plano, que el
 * dominio reciba correos (consulta DNS).
 */
public class RegistroControlador {

    @FXML private TextField nombre;
    @FXML private TextField email;
    @FXML private PasswordField password;
    @FXML private CheckBox acepto;
    @FXML private Label error;
    @FXML private Label errorEmail;
    @FXML private Button botonCrear;

    private final Servicios s = Servicios.get();

    @FXML
    private void initialize() {
        email.focusedProperty().addListener((obs, antes, ahora) -> {
            if (!ahora && !email.getText().isBlank()) revisarCorreo(() -> { }, () -> { });
        });
        email.textProperty().addListener((obs, a, b) -> {
            Vistas.ocultar(errorEmail);
            Vistas.clase(email, "campo-error", false);
        });
    }

    /** Valida el correo sin congelar la ventana y luego llama a siValido o siInvalido. */
    private void revisarCorreo(Runnable siValido, Runnable siInvalido) {
        String limpio = CuentaService.limpiarCorreo(email.getText());
        String errorFormato = CuentaService.validarFormatoCorreo(limpio);
        if (errorFormato != null) {
            marcarCorreo(errorFormato);
            siInvalido.run();
            return;
        }
        Task<String> tarea = new Task<>() {
            @Override
            protected String call() {
                return s.cuentas.validarCorreo(limpio);
            }
        };
        tarea.setOnSucceeded(e -> {
            marcarCorreo(tarea.getValue());
            (tarea.getValue() == null ? siValido : siInvalido).run();
        });
        // Si la consulta falla por la red, decide el registro (que vuelve a validar).
        tarea.setOnFailed(e -> {
            marcarCorreo(null);
            siValido.run();
        });
        Thread t = new Thread(tarea, "validar-correo");
        t.setDaemon(true);
        t.start();
    }

    private void marcarCorreo(String mensaje) {
        Vistas.mostrarError(errorEmail, mensaje);
        Vistas.clase(email, "campo-error", mensaje != null);
    }

    @FXML
    private void crear() {
        Vistas.ocultar(error);
        if (password.getText().length() < 8) {
            Vistas.mostrarError(error, "La contraseña debe tener al menos 8 caracteres");
            return;
        }
        if (!acepto.isSelected()) {
            Vistas.mostrarError(error, "Debes aceptar los Términos y condiciones y la Política de tratamiento de datos personales");
            return;
        }
        botonCrear.setDisable(true);
        botonCrear.setText("Revisando el correo...");
        revisarCorreo(this::registrar, this::restaurarBoton);
    }

    private void registrar() {
        try {
            Usuario u = s.cuentas.registrar(email.getText(), password.getText(), nombre.getText(), acepto.isSelected());
            Sesion.iniciar(u);
            Navegador.irAPrincipal();
            Navegador.<PerfilesControlador>mostrar(Pantalla.PERFILES).bienvenida();
        } catch (CuentaService.ErrorCuenta e) {
            Vistas.mostrarError(error, e.getMessage());
            restaurarBoton();
        }
    }

    private void restaurarBoton() {
        botonCrear.setDisable(false);
        botonCrear.setText("Crear cuenta");
    }

    @FXML
    private void verTerminos() {
        Navegador.mostrarDocumentoLegal("terminos");
    }

    @FXML
    private void verPrivacidad() {
        Navegador.mostrarDocumentoLegal("privacidad");
    }

    @FXML
    private void irALogin() {
        Navegador.irALogin();
    }
}
