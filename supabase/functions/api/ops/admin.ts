// Administración. El rol se revisa en el servidor (exigirAdmin en index.ts)
// antes de llegar aquí.
import { iso, sql } from "../lib/bd.ts";
import { type Cuerpo, entero, malaPeticion, noEncontrado, texto } from "../lib/http.ts";
import { aUsuario, COLUMNAS_USUARIO, conPlan } from "../lib/sesion.ts";
import { planEfectivo } from "../../_shared/dominio/plan.ts";
import { CATALOGO } from "../../_shared/dominio/catalogo.generado.ts";
import { sincronizar } from "./perfiles.ts";
import { lanzar } from "./job.ts";

export async function resumen() {
  const [r] = await sql`
    SELECT (SELECT count(*) FROM secop.users) AS usuarios, (SELECT count(*) FROM secop.perfiles) AS perfiles,
           (SELECT count(*) FROM secop.procesos) AS procesos, (SELECT count(*) FROM secop.coincidencias) AS coincidencias,
           (SELECT max(ingresado_at) FROM secop.procesos) AS ultimo_ingreso`;
  const ranking = await sql`SELECT profesion_id, count(*) AS n FROM secop.perfiles
                            WHERE profesion_id IS NOT NULL GROUP BY profesion_id ORDER BY n DESC`;
  return {
    usuarios: r.usuarios, perfiles: r.perfiles, procesos: r.procesos, coincidencias: r.coincidencias,
    ultimoIngreso: iso(r.ultimo_ingreso), rankingProfesiones: ranking.map((f) => ({ profesion_id: f.profesion_id, n: f.n })),
  };
}

export async function usuarios() {
  const filas = await sql.unsafe(`SELECT ${COLUMNAS_USUARIO} FROM secop.users ORDER BY id`);
  return { usuarios: filas.map((f) => conPlan(aUsuario(f))) };
}

/** Últimas corridas: solo dos tablas fijas, nunca un nombre que mande la app. */
export async function historial(c: Cuerpo) {
  const tipo = texto(c, "tipo", 20, true);
  const n = Math.min(Math.max(entero(c, "n", false) ?? 8, 1), 50);
  const filas = tipo === "ingesta"
    ? await sql`SELECT * FROM secop.ingesta_log ORDER BY corrida_at DESC, id DESC LIMIT ${n}`
    : tipo === "coincidencias"
    ? await sql`SELECT * FROM secop.coincidencias_log ORDER BY corrida_at DESC, id DESC LIMIT ${n}`
    : null;
  if (filas == null) throw malaPeticion("tipo debe ser ingesta o coincidencias.");
  return { filas: filas.map((f) => ({ ...f, corrida_at: iso(f.corrida_at) })) };
}

/** Cambia algo del usuario y, en la misma transacción, ajusta sus perfiles al plan nuevo. */
async function cambiarYSincronizar(usuarioId: number, cambio: (tx: typeof sql) => Promise<unknown>) {
  return await sql.begin(async (tx) => {
    await cambio(tx as unknown as typeof sql);
    const filas = await tx.unsafe(`SELECT ${COLUMNAS_USUARIO} FROM secop.users WHERE id = $1`, [usuarioId]);
    if (filas.length === 0) throw noEncontrado("No existe ese usuario.");
    const u = aUsuario(filas[0]);
    await sincronizar(tx, u.id, planEfectivo(u));
    return conPlan(u);
  });
}

export async function asignarPlan(c: Cuerpo) {
  const usuarioId = entero(c, "usuarioId")!;
  const plan = texto(c, "plan", 20, true)!;
  if (!CATALOGO.planes.some((p) => p.id === plan)) throw malaPeticion("Plan no válido.");
  return await cambiarYSincronizar(usuarioId, (tx) => tx`UPDATE secop.users SET plan = ${plan}, gracia_hasta = NULL WHERE id = ${usuarioId}`);
}

export async function asignarRol(c: Cuerpo) {
  const usuarioId = entero(c, "usuarioId")!;
  const rol = texto(c, "rol", 20, true)!;
  if (rol !== "admin" && rol !== "usuario") throw malaPeticion("Rol no válido.");
  return await cambiarYSincronizar(usuarioId, (tx) => tx`UPDATE secop.users SET rol = ${rol} WHERE id = ${usuarioId}`);
}

export async function reiniciarPrueba(c: Cuerpo) {
  const usuarioId = entero(c, "usuarioId")!;
  const hasta = new Date(Date.now() + CATALOGO.diasPrueba * 86_400_000);
  return await cambiarYSincronizar(usuarioId, (tx) => tx`UPDATE secop.users SET prueba_hasta = ${hasta} WHERE id = ${usuarioId}`);
}

export function actualizarAhora(c: Cuerpo) {
  const dias = Math.min(Math.max(entero(c, "dias", false) ?? 5, 1), 30);
  lanzar("job.ingesta", { offset: 0, dias, leidos: 0, nuevos: 0, actualizados: 0 });
  return { ok: true, mensaje: "Actualización iniciada en el servidor." };
}
