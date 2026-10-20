package co.secopradar.web;

import co.secopradar.dominio.Catalogo;
import co.secopradar.dominio.Plan;
import co.secopradar.repositorio.UsuarioRepositorio;
import co.secopradar.servicio.AlertasService;
import co.secopradar.servicio.BusquedaService;
import co.secopradar.servicio.LegalService;
import co.secopradar.servicio.LimiteIntentos;
import co.secopradar.servicio.PortadaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;
import java.util.Optional;

/** Páginas públicas: portada, búsqueda, documentos legales y baja de alertas. */
@Controller
public class InicioController {

    private final PortadaService portada;
    private final BusquedaService busqueda;
    private final Catalogo catalogo;
    private final LegalService legal;
    private final LimiteIntentos limite;
    private final AlertasService alertas;
    private final UsuarioRepositorio usuarios;
    private final String correoContacto;

    public InicioController(PortadaService portada, BusquedaService busqueda, Catalogo catalogo, LegalService legal,
                            LimiteIntentos limite, AlertasService alertas, UsuarioRepositorio usuarios,
                            @Value("${secop.correo-contacto}") String correoContacto) {
        this.portada = portada;
        this.busqueda = busqueda;
        this.catalogo = catalogo;
        this.legal = legal;
        this.limite = limite;
        this.alertas = alertas;
        this.usuarios = usuarios;
        this.correoContacto = correoContacto;
    }

    @GetMapping("/")
    public String inicio(Model model) {
        model.addAttribute("datos", portada.datos());
        model.addAttribute("sectores", PortadaService.SECTORES);
        model.addAttribute("planes", Plan.PLANES.values());
        model.addAttribute("diasPrueba", Plan.DIAS_PRUEBA);
        model.addAttribute("correoContacto", correoContacto);
        return "inicio";
    }

    @GetMapping("/buscar")
    public String buscar(@RequestParam(required = false) String texto,
                         @RequestParam(required = false) String profesion,
                         HttpServletRequest req, Model model) {
        model.addAttribute("profesiones", catalogo.profesiones());
        model.addAttribute("texto", texto == null ? "" : texto);
        model.addAttribute("profesionId", profesion == null ? "" : profesion);

        boolean porTexto = texto != null && !texto.isBlank();
        Optional<Catalogo.Profesion> prof = porTexto ? Optional.empty() : catalogo.profesion(profesion == null ? "" : profesion);
        if (!porTexto && prof.isEmpty()) return "buscar";

        if (!limite.permitido("buscar", Sesion.ip(req), 30, Duration.ofMinutes(1))) {
            model.addAttribute("error", "Demasiadas búsquedas. Espera un momento.");
            return "buscar";
        }
        if (porTexto && texto.length() > 200) {
            model.addAttribute("error", "La búsqueda es demasiado larga.");
            return "buscar";
        }
        BusquedaService.Resultado r = porTexto ? busqueda.porTexto(texto) : busqueda.porProfesion(prof.get());
        model.addAttribute("resultado", r);
        prof.ifPresent(p -> {
            model.addAttribute("profesion", p);
            if (p.contratacionDirecta()) model.addAttribute("aviso", catalogo.avisoContratacionDirecta());
        });
        return "buscar";
    }

    @GetMapping("/terminos")
    public String terminos(Model model) {
        model.addAttribute("titulo", "Términos y condiciones");
        model.addAttribute("html", legal.html("terminos"));
        return "legal";
    }

    @GetMapping("/privacidad")
    public String privacidad(Model model) {
        model.addAttribute("titulo", "Política de tratamiento de datos personales");
        model.addAttribute("html", legal.html("privacidad"));
        return "legal";
    }

    @GetMapping("/alertas/baja")
    public String bajaForm(@RequestParam int u, @RequestParam String t, Model model) {
        model.addAttribute("valido", alertas.firmaValida(u, t));
        model.addAttribute("u", u);
        model.addAttribute("t", t);
        return "baja";
    }

    @PostMapping("/alertas/baja")
    public String baja(@RequestParam int u, @RequestParam String t, Model model) {
        boolean valido = alertas.firmaValida(u, t);
        if (valido) usuarios.cambiarAlertas(u, false);
        model.addAttribute("valido", valido);
        model.addAttribute("hecho", valido);
        return "baja";
    }
}
