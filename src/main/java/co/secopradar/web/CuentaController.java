package co.secopradar.web;

import co.secopradar.dominio.Plan;
import co.secopradar.dominio.Usuario;
import co.secopradar.repositorio.UsuarioRepositorio;
import co.secopradar.servicio.AlertasService;
import co.secopradar.servicio.CuentaService;
import co.secopradar.util.Formato;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Mi cuenta, planes. */
@Controller
public class CuentaController {

    private final UsuarioRepositorio usuarios;
    private final CuentaService cuentas;
    private final AlertasService alertas;

    public CuentaController(UsuarioRepositorio usuarios, CuentaService cuentas, AlertasService alertas) {
        this.usuarios = usuarios;
        this.cuentas = cuentas;
        this.alertas = alertas;
    }

    @GetMapping("/cuenta")
    public String cuenta(HttpServletRequest req, Model model) {
        Usuario u = Sesion.usuario(req);
        Plan plan = Plan.efectivo(u);
        model.addAttribute("plan", plan);
        model.addAttribute("planHasta", Formato.fechaLarga(plan.hasta()));
        model.addAttribute("creada", Formato.fechaLarga(u.createdAt()));
        model.addAttribute("correoConfigurado", alertas.activas());
        return "cuenta";
    }

    @PostMapping("/cuenta/alertas")
    public String alertas(@RequestParam(defaultValue = "false") boolean activas, HttpServletRequest req, RedirectAttributes ra) {
        usuarios.cambiarAlertas(Sesion.usuario(req).id(), activas);
        ra.addFlashAttribute("mensaje", activas ? "Alertas por correo activadas." : "Alertas por correo desactivadas.");
        return "redirect:/cuenta";
    }

    @PostMapping("/cuenta/nombre")
    public String nombre(@RequestParam String nombre, HttpServletRequest req, RedirectAttributes ra) {
        String limpio = nombre.trim();
        usuarios.cambiarNombre(Sesion.usuario(req).id(), limpio.isEmpty() ? null : limpio.substring(0, Math.min(200, limpio.length())));
        ra.addFlashAttribute("mensaje", "Nombre actualizado.");
        return "redirect:/cuenta";
    }

    @PostMapping("/cuenta/password")
    public String password(@RequestParam String actual, @RequestParam String nueva, HttpServletRequest req, RedirectAttributes ra) {
        try {
            cuentas.cambiarPassword(Sesion.usuario(req), actual, nueva);
            ra.addFlashAttribute("mensaje", "Contraseña actualizada.");
        } catch (CuentaService.ErrorCuenta e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/cuenta";
    }

    @GetMapping("/planes")
    public String planes(HttpServletRequest req, Model model) {
        Usuario u = Sesion.usuario(req);
        model.addAttribute("plan", Plan.efectivo(u));
        model.addAttribute("planes", Plan.PLANES.values());
        model.addAttribute("diasPrueba", Plan.DIAS_PRUEBA);
        return "planes";
    }
}
