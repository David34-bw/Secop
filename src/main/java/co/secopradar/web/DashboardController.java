package co.secopradar.web;

import co.secopradar.dominio.Modalidades;
import co.secopradar.dominio.Plan;
import co.secopradar.dominio.RangoValor;
import co.secopradar.dominio.Region;
import co.secopradar.dominio.Usuario;
import co.secopradar.repositorio.PerfilRepositorio;
import co.secopradar.servicio.Filtros;
import co.secopradar.servicio.OportunidadesService;
import co.secopradar.util.Formato;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class DashboardController {

    /** Una opción de filtro ya resuelta para la plantilla. */
    public record Chip(String label, String href, boolean activo) {
    }

    public record Opcion(String valor, String label) {
    }

    private final OportunidadesService oportunidades;
    private final PerfilRepositorio perfiles;

    public DashboardController(OportunidadesService oportunidades, PerfilRepositorio perfiles) {
        this.oportunidades = oportunidades;
        this.perfiles = perfiles;
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam Map<String, String> params, HttpServletRequest req, Model model) {
        Usuario u = Sesion.usuario(req);
        Plan plan = Plan.efectivo(u);
        Filtros f = Filtros.leer(params);
        OportunidadesService.Panel panel = oportunidades.panel(u.id(), plan, f);

        model.addAttribute("plan", plan);
        model.addAttribute("f", f);
        model.addAttribute("panel", panel);
        model.addAttribute("tienePerfiles", !perfiles.deUsuario(u.id()).isEmpty());
        model.addAttribute("hayFiltros", f.contar() > 0);

        // Selectores
        model.addAttribute("regiones", Region.TODAS);
        model.addAttribute("departamentos", panel.departamentos().stream()
                .map(d -> new Opcion(d, Formato.labelDepartamento(d))).toList());
        model.addAttribute("modalidades", panel.modalidades().stream()
                .map(m -> new Opcion(m, Modalidades.label(m))).toList());
        model.addAttribute("ordenes", Filtros.OPCIONES_ORDEN);

        // Chips (enlaces ya armados)
        List<Chip> cierre = new ArrayList<>();
        cierre.add(new Chip("Cuando sea", f.conCierra("").href(), f.cierra().isEmpty()));
        for (String[] o : Filtros.OPCIONES_CIERRE) cierre.add(new Chip(o[1], f.conCierra(o[0]).href(), o[0].equals(f.cierra())));
        model.addAttribute("chipsCierre", cierre);
        model.addAttribute("chipsValor", RangoValor.TODOS.stream()
                .map(r -> new Chip(Formato.labelRango(r), f.alternarValor(r.id()).href(), f.tieneValor(r.id()))).toList());
        model.addAttribute("hrefSinFiltros", Filtros.sinFiltros(f.orden()).href());
        model.addAttribute("hrefMas", f.conLimite(f.limite() + Filtros.PASO_CARGAR_MAS).href());

        // Frase de resultados
        String departamento = f.departamento().isEmpty() ? null : "en " + Formato.labelDepartamento(f.departamento());
        String region = Region.porId(f.region()).map(r -> "en la región " + r.nombre()).orElse(null);
        String lugar = String.join(" y ", java.util.stream.Stream.of(departamento, region).filter(s -> s != null).toList());
        int t = panel.totalFiltrado();
        String plural = t == 1 ? "" : "s";
        String frase = t == 0 ? "Ningún resultado con estos filtros."
                : panel.hayMas()
                ? "Mostrando " + panel.oportunidades().size() + " de " + t + " resultado" + plural + (lugar.isEmpty() ? "" : " " + lugar) + "."
                : t + " resultado" + plural + (lugar.isEmpty() ? "" : " " + lugar) + ".";
        model.addAttribute("fraseResultados", frase);
        return "dashboard";
    }
}
