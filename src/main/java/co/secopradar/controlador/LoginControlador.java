package co.secopradar.controlador;

import co.secopradar.modelo.Sesion;
import co.secopradar.modelo.Usuario;
import co.secopradar.modelo.servicio.Servicios;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.time.Duration;
import java.util.Optional;

/** Controlador de login.fxml. */
public class LoginControlador {

    @FXML private TextField email;
    @FXML private PasswordField password;
    @FXML private Label error;
    @FXML private Button botonIngresar;

    private final Servicios s = Servicios.get();

    @FXML
    private void initialize() {
        email.requestFocus();
    }

    @FXML
    private void ingresar() {
        String correo = email.getText().trim().toLowerCase();
        if (correo.isEmpty() || password.getText().isEmpty()) {
            Vistas.mostrarError(error, "Escribe tu correo y tu contraseña");
            return;
        }
        // Límite de intentos por correo: frena a quien prueba contraseñas.
        if (!s.limiteIntentos.permitido("login", correo, 10, Duration.ofMinutes(15))) {
            Vistas.mostrarError(error, "Demasiados intentos. Intenta de nuevo en unos minutos.");
            return;
        }
        Optional<Usuario> u = s.cuentas.autenticar(correo, password.getText());
        if (u.isEmpty()) {
            Vistas.mostrarError(error, "Correo o contraseña incorrectos");
            password.clear();
            password.requestFocus();
            return;
        }
        Sesion.iniciar(u.get());
        Navegador.irAPrincipal();
    }

    @FXML
    private void irARegistro() {
        Navegador.irARegistro();
    }
}
