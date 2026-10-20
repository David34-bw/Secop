package co.secopradar.servicio;

import co.secopradar.dominio.Plan;
import co.secopradar.dominio.Usuario;
import co.secopradar.repositorio.CoincidenciaRepositorio;
import co.secopradar.repositorio.LogRepositorio;
import co.secopradar.repositorio.UsuarioRepositorio;
import co.secopradar.util.Formato;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Alertas por correo de coincidencias nuevas (port de worker/alertas.mjs).
 * Solo envía si hay un servidor SMTP configurado (spring.mail.host) y
 * secop.alertas.enviar=true.
 */
@Service
public class AlertasService {

    private static final Logger log = LoggerFactory.getLogger(AlertasService.class);
    private static final int MAX_EN_CORREO = 15;

    private final UsuarioRepositorio usuarios;
    private final CoincidenciaRepositorio coincidencias;
    private final OportunidadesService oportunidades;
    private final LogRepositorio logs;
    private final ObjectProvider<JavaMailSender> correo;
    private final boolean enviar;
    private final int maxCorreosDia;
    private final String appUrl;
    private final String remitente;
    private final String secreto;

    public AlertasService(UsuarioRepositorio usuarios, CoincidenciaRepositorio coincidencias,
                          OportunidadesService oportunidades, LogRepositorio logs, ObjectProvider<JavaMailSender> correo,
                          @Value("${secop.alertas.enviar:false}") boolean enviar,
                          @Value("${secop.alertas.max-correos-dia:400}") int maxCorreosDia,
                          @Value("${secop.app-url:http://localhost:8080}") String appUrl,
                          @Value("${spring.mail.username:}") String remitente,
                          @Value("${secop.secreto}") String secreto) {
        this.usuarios = usuarios;
        this.coincidencias = coincidencias;
        this.oportunidades = oportunidades;
        this.logs = logs;
        this.correo = correo;
        this.enviar = enviar;
        this.maxCorreosDia = maxCorreosDia;
        this.appUrl = appUrl.replaceAll("/$", "");
        this.remitente = remitente;
        this.secreto = secreto;
    }

    public boolean correoConfigurado() {
        return correo.getIfAvailable() != null;
    }

    public boolean activas() {
        return enviar && correoConfigurado();
    }

    // Quienes pagan van primero si no alcanza el cupo diario.
    private static int prioridad(Plan p) {
        return switch (p.origen()) {
            case "pago", "gracia", "prepago" -> 0;
            case "prueba" -> 1;
            case "admin" -> 2;
            default -> 3;
        };
    }

    private record Pendiente(Usuario usuario, Plan plan, OportunidadesService.Pendientes datos) {
    }

    public void ejecutar() {
        if (!activas()) {
            log.info("[alertas] Envío desactivado (falta SMTP o secop.alertas.enviar no es true). No se envía nada.");
            return;
        }
        JavaMailSender sender = correo.getObject();
        int candidatos = 0, enviados = 0, fallidos = 0, omitidos = 0;
        List<String> errores = new ArrayList<>();
        try {
            List<Pendiente> pendientes = new ArrayList<>();
            for (Usuario u : usuarios.conAlertasActivas()) {
                Plan plan = Plan.efectivo(u);
                if (!plan.alertas()) continue;
                OportunidadesService.Pendientes p = oportunidades.alertasPendientes(u.id());
                if (!p.oportunidades().isEmpty()) pendientes.add(new Pendiente(u, plan, p));
            }
            candidatos = pendientes.size();
            pendientes.sort(Comparator.comparingInt((Pendiente p) -> prioridad(p.plan())).thenComparingInt(p -> p.usuario().id()));

            int cupo = maxCorreosDia - logs.alertasEnviadasDesde(Formato.inicioDeHoy());
            for (Pendiente p : pendientes) {
                if (cupo <= 0) {
                    omitidos++;
                    continue;
                }
                try {
                    enviarA(sender, p);
                } catch (Exception e) {
                    // Sin marcar: la próxima corrida lo vuelve a intentar.
                    fallidos++;
                    errores.add("usuario " + p.usuario().id() + ": " + e.getMessage());
                    log.error("[alertas]   usuario {}: falló el envío ({})", p.usuario().id(), e.getMessage());
                    continue;
                }
                cupo--;
                enviados++;
                // Solo después de que el correo salió: así no se repite ni se pierde.
                coincidencias.marcarNotificadas(p.datos().idsCoincidencias());
                logs.envioAlerta(p.usuario().id(), p.datos().oportunidades().size(), false);
            }
            log.info("[alertas] Terminado. {} enviados, {} fallidos, {} para mañana por el límite diario", enviados, fallidos, omitidos);
            logs.alertas(candidatos, enviados, fallidos, omitidos, fallidos == 0,
                    errores.isEmpty() ? null : String.join(" | ", errores.subList(0, Math.min(5, errores.size()))));
        } catch (RuntimeException e) {
            logs.alertas(candidatos, enviados, fallidos, omitidos, false, e.getMessage());
            throw e;
        }
    }

    private void enviarA(JavaMailSender sender, Pendiente p) throws Exception {
        List<Oportunidad> ops = p.datos().oportunidades();
        int total = ops.size();
        String perfilPrincipal = p.datos().porPerfil().entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
        String asunto = total + (total == 1 ? " oportunidad nueva" : " oportunidades nuevas")
                + (perfilPrincipal.isEmpty() ? "" : " para " + perfilPrincipal);

        StringBuilder html = new StringBuilder();
        html.append("<div style=\"font-family:Arial,sans-serif;max-width:600px;margin:auto;color:#1a2233\">")
                .append("<h2 style=\"color:#0f1c3d\">SECOP RADAR</h2>")
                .append("<p>Hola").append(p.usuario().nombre() == null ? "" : ", " + HtmlUtils.htmlEscape(p.usuario().nombre()))
                .append(". Encontramos ").append(total).append(total == 1 ? " oportunidad nueva" : " oportunidades nuevas")
                .append(" para ti:</p>");
        for (Oportunidad o : ops.subList(0, Math.min(MAX_EN_CORREO, total))) {
            html.append("<div style=\"border:1px solid #e6e8ee;border-radius:12px;padding:12px;margin:10px 0\">")
                    .append("<div style=\"font-size:12px;color:#5f6b7d\">").append(HtmlUtils.htmlEscape(o.entidad())).append("</div>")
                    .append("<div style=\"font-weight:bold;margin:4px 0\">").append(HtmlUtils.htmlEscape(o.objeto())).append("</div>")
                    .append("<div style=\"font-size:13px;color:#5f6b7d\">").append(o.valor()).append(" · ")
                    .append(HtmlUtils.htmlEscape(o.departamento() == null ? "" : o.departamento()))
                    .append(o.textoCierre() == null ? "" : " · " + o.textoCierre()).append("</div>");
            if (o.url() != null) {
                html.append("<a href=\"").append(HtmlUtils.htmlEscape(o.url())).append("\" style=\"color:#2b4fb0\">Ver en SECOP</a>");
            }
            html.append("</div>");
        }
        if (total > MAX_EN_CORREO) html.append("<p>Y ").append(total - MAX_EN_CORREO).append(" más en tu panel.</p>");
        html.append("<p><a href=\"").append(appUrl).append("/dashboard\" style=\"background:#e8a33d;color:#0f1c3d;")
                .append("padding:10px 18px;border-radius:999px;text-decoration:none;font-weight:bold\">Ver mi panel</a></p>")
                .append("<p style=\"font-size:12px;color:#6b7280\"><a href=\"").append(urlBaja(p.usuario().id()))
                .append("\" style=\"color:#6b7280\">Dejar de recibir alertas</a></p></div>");

        MimeMessage msg = sender.createMimeMessage();
        MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
        if (!remitente.isBlank()) h.setFrom(remitente, "SECOP RADAR");
        h.setTo(p.usuario().email());
        h.setSubject(asunto);
        h.setText("Tienes " + asunto + ". Míralas en " + appUrl + "/dashboard", html.toString());
        msg.setHeader("List-Unsubscribe", "<" + urlBaja(p.usuario().id()) + ">");
        sender.send(msg);
    }

    // ---- Enlace de baja firmado ----

    public String firma(int userId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec((secreto + ":baja").getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(String.valueOf(userId).getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean firmaValida(int userId, String firma) {
        return firma != null && MessageDigest.isEqual(firma(userId).getBytes(StandardCharsets.UTF_8), firma.getBytes(StandardCharsets.UTF_8));
    }

    public String urlBaja(int userId) {
        return appUrl + "/alertas/baja?u=" + userId + "&t=" + firma(userId);
    }
}
