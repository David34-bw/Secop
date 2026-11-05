// Procesos de SECOP y oportunidades del usuario (lectura).
import { iso, sql } from "../lib/bd.ts";
import { type Cuerpo, malaPeticion, texto } from "../lib/http.ts";
import type { Usuario } from "../lib/sesion.ts";
import { panelVisible } from "../../_shared/dominio/oportunidades.ts";
import { planEfectivo } from "../../_shared/dominio/plan.ts";

export const COLUMNAS_PROCESO = "id_del_proceso, entidad, nombre, objeto, modalidad, categoria, tipo_contrato, valor, "
  + "departamento, ciudad, estado_apertura, fecha_publicacion, fecha_cierre, url, ordenentidad";

/** Fila de procesos con los nombres del record Proceso de Java. */
// deno-lint-ignore no-explicit-any
export function aProceso(r: any, prefijo = "") {
  const v = (c: string) => r[prefijo + c];
  return {
    id: v("id_del_proceso"), entidad: v("entidad"), nombre: v("nombre"), objeto: v("objeto"), modalidad: v("modalidad"),
    categoria: v("categoria"), tipoContrato: v("tipo_contrato"), valor: v("valor"), departamento: v("departamento"),
    ciudad: v("ciudad"), estadoApertura: v("estado_apertura"), fechaPublicacion: iso(v("fecha_publicacion")),
    fechaCierre: iso(v("fecha_cierre")), url: v("url"), ordenEntidad: v("ordenentidad"),
  };
}

/** Pantalla de ingreso y barra de estado: sin sesión, solo cifras públicas. */
export async function estadoDatos() {
  const [r] = await sql`SELECT count(*) AS procesos, max(ingresado_at) AS ultimo FROM secop.procesos`;
  return { procesos: r.procesos, ultimoIngreso: iso(r.ultimo) };
}

/** Lo que necesita PortadaService (Inicio): los tres agregados de ProcesoDAO. */
export async function inicioDatos() {
  const ahora = new Date();
  const inicioHoy = new Date(new Date(Date.now() - 5 * 3_600_000).toISOString().substring(0, 10) + "T05:00:00Z");
  const enSemana = new Date(ahora.getTime() + 7 * 86_400_000);
  const hace3 = new Date(ahora.getTime() - 3 * 86_400_000);
  const [resumen] = await sql`
    SELECT count(*) AS abiertos,
           sum(CASE WHEN ingresado_at >= ${inicioHoy} THEN 1 ELSE 0 END) AS nuevos_hoy,
           sum(CASE WHEN fecha_cierre < ${enSemana} THEN 1 ELSE 0 END) AS cierran_semana
    FROM secop.procesos WHERE estado_apertura = 'Abierto' AND fecha_cierre >= ${ahora}`;
  const porDepartamentoYTipo = await sql`
    SELECT departamento, tipo_contrato AS tipo, count(*) AS n FROM secop.procesos
    WHERE estado_apertura = 'Abierto' AND fecha_cierre >= ${ahora} GROUP BY departamento, tipo_contrato`;
  const [ofertas] = await sql`
    SELECT count(*) AS cerrados, sum(CASE WHEN respuestas <= 1 THEN 1 ELSE 0 END) AS una_o_ninguna,
           min(fecha_publicacion) AS desde FROM secop.procesos
    WHERE modalidad = 'Mínima cuantía' AND fecha_cierre < ${hace3}
      AND estado_procedimiento IN ('Evaluación', 'Seleccionado', 'Adjudicado') AND respuestas IS NOT NULL`;
  return {
    resumen: { abiertos: resumen.abiertos, nuevos_hoy: Number(resumen.nuevos_hoy ?? 0), cierran_semana: Number(resumen.cierran_semana ?? 0) },
    porDepartamentoYTipo: porDepartamentoYTipo.map((f) => ({ departamento: f.departamento, tipo: f.tipo, n: f.n })),
    ofertasMinimaCuantia: { cerrados: ofertas.cerrados, una_o_ninguna: Number(ofertas.una_o_ninguna ?? 0), desde: iso(ofertas.desde) },
  };
}

/**
 * Procesos vigentes para Buscar y la vista previa de perfiles (el matching
 * sigue en la app). `desde` (ISO) devuelve solo los que entraron o cambiaron
 * después, para que la app no baje todo cada vez.
 */
export async function vigentes(c: Cuerpo) {
  const desde = texto(c, "desde", 40);
  if (desde != null && Number.isNaN(Date.parse(desde))) throw malaPeticion("desde debe ser una fecha ISO.");
  const ahora = new Date();
  const limite = new Date(ahora.getTime() - 2 * 86_400_000);
  const filas = desde == null
    ? await sql.unsafe(`SELECT ${COLUMNAS_PROCESO} FROM secop.procesos WHERE fecha_cierre IS NULL OR fecha_cierre >= $1`, [limite])
    : await sql.unsafe(
      `SELECT ${COLUMNAS_PROCESO} FROM secop.procesos WHERE (fecha_cierre IS NULL OR fecha_cierre >= $1) AND actualizado_at > $2`,
      [limite, new Date(desde)],
    );
  return { procesos: filas.map((r) => aProceso(r)), hasta: ahora.toISOString() };
}

export async function porIds(c: Cuerpo) {
  const ids = c.ids;
  if (!Array.isArray(ids) || ids.length > 500 || ids.some((x) => typeof x !== "string" || x.length > 100)) {
    throw malaPeticion("ids debe ser una lista de hasta 500 identificadores.");
  }
  if (ids.length === 0) return { procesos: [] };
  const filas = await sql.unsafe(`SELECT ${COLUMNAS_PROCESO} FROM secop.procesos WHERE id_del_proceso = ANY($1)`, [ids]);
  return { procesos: filas.map((r) => aProceso(r)) };
}

/**
 * Oportunidades del usuario: coincidencias de sus perfiles activos con
 * procesos vigentes, sin duplicados, sin ocultos (salvo que los pida) y con
 * el límite de su plan aplicado aquí, no en la app.
 */
export async function oportunidades(u: Usuario, c: Cuerpo) {
  const incluirOcultas = c.incluirOcultas === true;
  const cols = COLUMNAS_PROCESO.split(", ").map((x) => "p." + x + " AS p_" + x).join(", ");
  const filas = await sql.unsafe(
    `SELECT c.id AS c_id, c.score, c.notificado, pf.id AS pf_id, pf.nombre AS perfil, ${cols}
     FROM secop.coincidencias c JOIN secop.perfiles pf ON pf.id = c.perfil_id
     JOIN secop.procesos p ON p.id_del_proceso = c.proceso_id
     WHERE pf.user_id = $1 AND pf.activo AND (p.fecha_cierre IS NULL OR p.fecha_cierre >= $2)`,
    [u.id, new Date()],
  );
  const todas = filas.map((r) => ({
    id: r.c_id, score: r.score, notificado: r.notificado, perfilId: r.pf_id, perfil: r.perfil, proceso: aProceso(r, "p_"),
  }));
  const ocultos = await sql`SELECT proceso_id FROM secop.procesos_ocultos WHERE usuario_id = ${u.id}`;
  const plan = planEfectivo(u);
  return panelVisible(todas, new Set(ocultos.map((o) => o.proceso_id)), incluirOcultas, plan.maxCoincidencias);
}

export async function marcarVistas(u: Usuario, c: Cuerpo) {
  const ids = c.ids;
  if (!Array.isArray(ids) || ids.length > 5000 || ids.some((x) => !Number.isSafeInteger(x))) {
    throw malaPeticion("ids debe ser una lista de números.");
  }
  if (ids.length > 0) {
    await sql`UPDATE secop.coincidencias SET notificado = TRUE WHERE id = ANY(${ids})
              AND perfil_id IN (SELECT id FROM secop.perfiles WHERE user_id = ${u.id})`;
  }
  return { ok: true };
}

export async function ocultos(u: Usuario) {
  const filas = await sql`SELECT proceso_id FROM secop.procesos_ocultos WHERE usuario_id = ${u.id}`;
  return { procesos: filas.map((f) => f.proceso_id) };
}

export async function ocultar(u: Usuario, c: Cuerpo, ocultarlo: boolean) {
  const procesoId = texto(c, "procesoId", 100, true)!;
  if (ocultarlo) {
    await sql`INSERT INTO secop.procesos_ocultos (usuario_id, proceso_id)
              SELECT ${u.id}, id_del_proceso FROM secop.procesos WHERE id_del_proceso = ${procesoId}
              ON CONFLICT DO NOTHING`;
  } else {
    await sql`DELETE FROM secop.procesos_ocultos WHERE usuario_id = ${u.id} AND proceso_id = ${procesoId}`;
  }
  return { ok: true };
}
