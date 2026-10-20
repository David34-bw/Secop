package co.secopradar.web;

import co.secopradar.dominio.Catalogo;
import co.secopradar.dominio.Plan;
import co.secopradar.dominio.Usuario;
import co.secopradar.repositorio.CoincidenciaRepositorio;
import co.secopradar.repositorio.LogRepositorio;
import co.secopradar.repositorio.PerfilRepositorio;
import co.secopradar.repositorio.ProcesoRepositorio;
import co.secopradar.repositorio.UsuarioRepositorio;
import co.secopradar.servicio.AlertasService;
import co.secopradar.servicio.CronService;
import co.secopradar.servicio.PlanesService;
import co.secopradar.util.Formato;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Panel de administración (port de app/(app)/admin). */
@Controller
public class AdminController {

    public record FilaUsuario(Usuario usuario, Plan plan, String creada) {
    }

    private final UsuarioRepositorio usuarios;
    private final PerfilRepositorio perfiles;
    private final ProcesoRepositorio procesos;
    private final CoincidenciaRepositorio coincidencias;
    private final LogRepositorio logs;
    private final CronService cron;
    private final PlanesService planes;
    private final AlertasService alertas;
    private final Catalogo catalogo;

    public AdminController(UsuarioRepositorio usuarios, PerfilRepositorio perfiles, ProcesoRepositorio procesos,
                           CoincidenciaRepositorio coincidencias, LogRepositorio logs, CronService cron,
                           PlanesService planes, AlertasService alertas, Catalogo catalogo) {
        this.usuarios = usuarios;
        this.perfiles = perfiles;
        this.procesos = procesos;
        this.coincidencias = coincidencias;
        this.logs = logs;
        this.cron = cron;
        this.planes = planes;
        this.alertas = alertas;
        this.catalogo = catalogo;
    }

    @GetMapping("/admin")
    public String admin(Model model) {
        model.addAttribute("totalUsuarios", usuarios.contar());
        model.addAttribute("totalPerfiles", perfiles.contar());
        model.addAttribute("totalProcesos", Formato.numero(procesos.contar()));
        model.addAttribute("totalCoincidencias", Formato.numero(coincidencias.contar()));
        model.addAttribute("ultimoIngreso", Formato.fechaHora(procesos.ultimoIngreso()));
        model.addAttribute("logIngesta", formatear(logs.ultimas("ingesta_log", 8)));
        model.addAttribute("logCoincidencias", formatear(logs.ultimas("coincidencias_log", 8)));
        model.addAttribute("logAlertas", formatear(logs.ultimas("alertas_log", 5)));
        model.addAttribute("cronCorriendo", cron.estaCorriendo());
        model.addAttribute("cronResultado", cron.ultimoResultado());
        model.addAttribute("alertasActivas", alertas.activas());
        model.addAttribute("usuarios", usuarios.todos().stream()
                .map(u -> new FilaUsuario(u, Plan.efectivo(u), Formato.fechaLarga(u.createdAt()))).toList());
        model.addAttribute("planes", Plan.PLANES.keySet());
        model.addAttribute("ranking", perfiles.rankingProfesiones().stream().map(f -> Map.of(
                "nombre", catalogo.profesion((String) f.get("profesion_id")).map(Catalogo.Profesion::nombre).orElse((String) f.get("profesion_id")),
                "n", f.get("n"))).toList());
        return "admin";
    }

    private static List<Map<String, Object>> formatear(List<Map<String, Object>> filas) {
        filas.forEach(f -> {
            Object c = f.get("corrida_at");
            if (c instanceof OffsetDateTime o) f.put("corrida", Formato.fechaHora(o));
            else if (c instanceof java.sql.Timestamp t) f.put("corrida", Formato.fechaHora(t.toInstant().atOffset(java.time.ZoneOffset.UTC)));
            else f.put("corrida", String.valueOf(c));
        });
        return filas;
    }

    @PostMapping("/admin/cron")
    public String correrCron(@RequestParam(defaultValue = "5") int dias, RedirectAttributes ra) {
        int d = Math.max(1, Math.min(dias, 60));
        boolean ok = cron.correrEnSegundoPlano(d);
        ra.addFlashAttribute("mensaje", ok
                ? "Actualización iniciada (" + d + " días). Puede tardar unos minutos: recarga esta página para ver el resultado."
                : "Ya hay una actualización en curso.");
        return "redirect:/admin";
    }

    @PostMapping("/admin/usuarios/{id}/plan")
    public String asignarPlan(@PathVariable int id, @RequestParam String plan, RedirectAttributes ra) {
        if (Plan.PLANES.containsKey(plan)) {
            usuarios.asignarPlan(id, plan);
            usuarios.porId(id).ifPresent(u -> planes.sincronizarPerfiles(id, Plan.efectivo(u)));
            ra.addFlashAttribute("mensaje", "Plan actualizado.");
        }
        return "redirect:/admin";
    }

    @PostMapping("/admin/usuarios/{id}/prueba")
    public String reiniciarPrueba(@PathVariable int id, RedirectAttributes ra) {
        usuarios.reiniciarPrueba(id, OffsetDateTime.now().plusDays(Plan.DIAS_PRUEBA));
        usuarios.porId(id).ifPresent(u -> planes.sincronizarPerfiles(id, Plan.efectivo(u)));
        ra.addFlashAttribute("mensaje", "Prueba de " + Plan.DIAS_PRUEBA + " días reiniciada.");
        return "redirect:/admin";
    }

    @PostMapping("/admin/usuarios/{id}/rol")
    public String rol(@PathVariable int id, @RequestParam String rol, HttpServletRequest req, RedirectAttributes ra) {
        if (Sesion.usuario(req).id() == id) {
            ra.addFlashAttribute("error", "No puedes cambiar tu propio rol.");
        } else if (rol.equals("admin") || rol.equals("usuario")) {
            usuarios.asignarRol(id, rol);
            ra.addFlashAttribute("mensaje", "Rol actualizado.");
        }
        return "redirect:/admin";
    }
}
