package co.secopradar.web;

import co.secopradar.dominio.Plan;
import co.secopradar.dominio.Usuario;
import co.secopradar.servicio.CuentaService;
import co.secopradar.servicio.LimiteIntentos;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller
public class AuthController {

    private final CuentaService cuentas;
    private final LimiteIntentos limite;

    public AuthController(CuentaService cuentas, LimiteIntentos limite) {
        this.cuentas = cuentas;
        this.limite = limite;
    }

    @GetMapping("/login")
    public String loginForm(HttpServletRequest req) {
        return Sesion.id(req) != null ? "redirect:/dashboard" : "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email, @RequestParam String password,
                        HttpServletRequest req, Model model) {
        model.addAttribute("email", email);
        if (!limite.permitido("login", Sesion.ip(req), 10, Duration.ofMinutes(15))) {
            model.addAttribute("error", "Demasiados intentos. Intenta de nuevo en unos minutos.");
            return "login";
        }
        Optional<Usuario> u = cuentas.autenticar(email, password);
        if (u.isEmpty()) {
            model.addAttribute("error", "Correo o contraseña incorrectos");
            return "login";
        }
        Sesion.iniciar(req, u.get());
        return "redirect:/dashboard";
    }

    @GetMapping("/registro")
    public String registroForm(HttpServletRequest req, Model model) {
        if (Sesion.id(req) != null) return "redirect:/dashboard";
        model.addAttribute("diasPrueba", Plan.DIAS_PRUEBA);
        return "registro";
    }

    @PostMapping("/registro")
    public String registro(@RequestParam String email, @RequestParam String password,
                           @RequestParam(required = false) String nombre,
                           @RequestParam(defaultValue = "false") boolean acepto,
                           HttpServletRequest req, Model model) {
        model.addAttribute("email", email);
        model.addAttribute("nombre", nombre);
        model.addAttribute("diasPrueba", Plan.DIAS_PRUEBA);
        if (!limite.permitido("registro", Sesion.ip(req), 5, Duration.ofHours(1))) {
            model.addAttribute("error", "Demasiados intentos. Intenta de nuevo más tarde.");
            return "registro";
        }
        try {
            Usuario u = cuentas.registrar(email, password, nombre, acepto);
            Sesion.iniciar(req, u);
            return "redirect:/perfiles?bienvenida";
        } catch (CuentaService.ErrorCuenta e) {
            model.addAttribute("error", e.getMessage());
            return "registro";
        }
    }

    /** Validación en vivo del formulario de registro (la decisión final la toma POST /registro). */
    @GetMapping("/api/validar-correo")
    @ResponseBody
    public Map<String, Object> validarCorreo(@RequestParam(defaultValue = "") String email, HttpServletRequest req) {
        String limpio = CuentaService.limpiarCorreo(email);
        String error = CuentaService.validarFormatoCorreo(limpio);
        // La consulta DNS solo si el formato pasó, y con límite por IP.
        if (error == null && limite.permitido("validar-correo", Sesion.ip(req), 30, Duration.ofMinutes(1))) {
            error = cuentas.validarCorreo(limpio);
        }
        Map<String, Object> r = new HashMap<>();
        r.put("ok", error == null);
        r.put("error", error);
        return r;
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest req) {
        Sesion.cerrar(req);
        return "redirect:/";
    }
}
