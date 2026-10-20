package co.secopradar.web;

import co.secopradar.dominio.Usuario;
import co.secopradar.repositorio.UsuarioRepositorio;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Optional;

/** Protege las rutas de la app: sin sesión, a /login; /admin solo para administradores. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final UsuarioRepositorio usuarios;

    public WebConfig(UsuarioRepositorio usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
                Integer id = Sesion.id(req);
                Optional<Usuario> u = id == null ? Optional.empty() : usuarios.porId(id);
                if (u.isEmpty()) {
                    if (req.getRequestURI().startsWith("/api/")) {
                        res.setStatus(401);
                        res.setContentType("application/json;charset=UTF-8");
                        res.getWriter().write("{\"error\":\"No autorizado\"}");
                    } else {
                        res.sendRedirect("/login");
                    }
                    return false;
                }
                if (req.getRequestURI().startsWith("/admin") && !u.get().esAdmin()) {
                    res.sendRedirect("/dashboard");
                    return false;
                }
                req.setAttribute(Sesion.ATRIBUTO_USUARIO, u.get());
                return true;
            }
        }).addPathPatterns("/dashboard/**", "/perfiles/**", "/cuenta/**", "/planes/**", "/admin/**", "/api/perfiles/**");
    }
}
