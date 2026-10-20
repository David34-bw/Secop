package co.secopradar.dominio;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Planes del servicio: precios y límites en un solo lugar (port de
 * src/lib/planes.js). maxPerfiles y maxCoincidencias en null = sin límite.
 *
 * origen: admin | pago | gracia | prepago | prueba | gratis
 */
public record Plan(
        String id,
        String nombre,
        long precioMensual,
        Integer maxPerfiles,
        Integer maxCoincidencias,
        boolean alertas,
        String origen,
        OffsetDateTime hasta,
        Integer diasRestantes) {

    public static final Plan GRATIS = new Plan("gratis", "Gratis", 0, 1, 5, false, null, null, null);
    public static final Plan PROFESIONAL = new Plan("profesional", "Profesional", 69_900, 2, null, true, null, null, null);
    public static final Plan EMPRESA = new Plan("empresa", "Empresa", 149_900, 10, null, true, null, null, null);
    private static final Plan SIN_LIMITES = new Plan("admin", "Administrador", 0, null, null, true, null, null, null);

    public static final Map<String, Plan> PLANES = new LinkedHashMap<>();

    static {
        PLANES.put(GRATIS.id, GRATIS);
        PLANES.put(PROFESIONAL.id, PROFESIONAL);
        PLANES.put(EMPRESA.id, EMPRESA);
    }

    public static final List<String> PLANES_DE_PAGO = List.of("profesional", "empresa");
    public static final String PLAN_DE_PRUEBA = "profesional";
    public static final int DIAS_PRUEBA = 30;

    private static final Map<String, Integer> RANGO = Map.of("gratis", 0, "profesional", 1, "empresa", 2);

    private Plan con(String origen, OffsetDateTime hasta, Integer diasRestantes) {
        return new Plan(id, nombre, precioMensual, maxPerfiles, maxCoincidencias, alertas, origen, hasta, diasRestantes);
    }

    /** Lo que incluye cada plan, en palabras. */
    public List<String> beneficios() {
        List<String> b = new ArrayList<>();
        b.add(maxPerfiles == null ? "Perfiles de búsqueda ilimitados" : maxPerfiles == 1 ? "1 perfil de búsqueda" : maxPerfiles + " perfiles de búsqueda");
        b.add(maxCoincidencias == null ? "Todas tus oportunidades" : "Tus " + maxCoincidencias + " mejores oportunidades");
        b.add(alertas ? "Alertas diarias por correo" : "Sin alertas por correo");
        return b;
    }

    public String precioTexto() {
        return precioMensual == 0 ? "$0" : co.secopradar.util.Formato.pesos(precioMensual);
    }

    public static Plan efectivo(Usuario u) {
        return efectivo(u, Instant.now());
    }

    /**
     * Plan que rige ahora para un usuario. Se calcula en cada consulta, así
     * que la prueba o la gracia vencen solas a la hora exacta.
     */
    public static Plan efectivo(Usuario u, Instant ahora) {
        if (u.esAdmin()) return SIN_LIMITES.con("admin", null, null);

        Plan porSuscripcion = null;
        if (esDePago(u.plan())) {
            if (u.graciaHasta() == null) porSuscripcion = PLANES.get(u.plan()).con("pago", null, null);
            else if (vigente(u.graciaHasta(), ahora)) porSuscripcion = PLANES.get(u.plan()).con("gracia", u.graciaHasta(), null);
        }

        Plan porPrepago = esDePago(u.prepagoPlan()) && vigente(u.prepagoHasta(), ahora)
                ? PLANES.get(u.prepagoPlan()).con("prepago", u.prepagoHasta(), null)
                : null;

        if (porSuscripcion != null || porPrepago != null) {
            if (porPrepago == null) return porSuscripcion;
            if (porSuscripcion == null) return porPrepago;
            return RANGO.get(porPrepago.id) > RANGO.get(porSuscripcion.id) ? porPrepago : porSuscripcion;
        }

        if (vigente(u.pruebaHasta(), ahora)) {
            long ms = Duration.between(ahora, u.pruebaHasta().toInstant()).toMillis();
            int dias = (int) Math.ceil(ms / 86_400_000.0);
            return PLANES.get(PLAN_DE_PRUEBA).con("prueba", u.pruebaHasta(), dias);
        }

        return GRATIS.con("gratis", null, null);
    }

    // List.of(...).contains(null) lanza NullPointerException.
    public static boolean esDePago(String plan) {
        return plan != null && PLANES_DE_PAGO.contains(plan);
    }

    private static boolean vigente(OffsetDateTime fecha, Instant ahora) {
        return fecha != null && fecha.toInstant().isAfter(ahora);
    }

    public String mensajeLimitePerfiles() {
        int n = maxPerfiles;
        String s = n == 1 ? "" : "es";
        return "Tu plan " + nombre + " permite " + n + " perfil" + s + " activo" + (n == 1 ? "" : "s")
                + ". Elimina uno o mejora tu plan para crear otro.";
    }
}
