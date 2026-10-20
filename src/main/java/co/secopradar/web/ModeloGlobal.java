package co.secopradar.web;

import co.secopradar.dominio.Usuario;
import co.secopradar.repositorio.UsuarioRepositorio;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Datos que usan todas las plantillas (encabezado y navegación). */
@ControllerAdvice
public class ModeloGlobal {

    private final UsuarioRepositorio usuarios;

    public ModeloGlobal(UsuarioRepositorio usuarios) {
        this.usuarios = usuarios;
    }

    @ModelAttribute("usuario")
    public Usuario usuario(HttpServletRequest req) {
        Usuario u = Sesion.usuario(req);
        if (u != null) return u;
        Integer id = Sesion.id(req);
        return id == null ? null : usuarios.porId(id).orElse(null);
    }

    @ModelAttribute("ruta")
    public String ruta(HttpServletRequest req) {
        return req.getRequestURI();
    }
}
