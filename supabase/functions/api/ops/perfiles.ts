// Perfiles de búsqueda. Crear y borrar son una sola transacción en el
// servidor: límite del plan, guardar y cruzar (o reactivar) van juntos.
import { iso, sql, type Tx } from "../lib/bd.ts";
import { type Cuerpo, entero, malaPeticion } from "../lib/http.ts";
import type { Usuario } from "../lib/sesion.ts";
import { calcularScore, type ProcesoMatch } from "../../_shared/dominio/matching.ts";
import { validarPerfil } from "../../_shared/dominio/perfil.ts";
import { cambiosPorPlan, mensajeLimitePerfiles, planEfectivo, type Plan } from "../../_shared/dominio/plan.ts";
import { aProceso, COLUMNAS_PROCESO } from "./procesos.ts";

function lista(v: unknown): string[] {
  try {
    const l = typeof v === "string" ? JSON.parse(v) : v;
    return Array.isArray(l) ? l.filter((x) => typeof x === "string") : [];
  } catch {
    return [];
  }
}

/** Fila de perfiles con los nombres del record Perfil de Java. */
// deno-lint-ignore no-explicit-any
export function aPerfil(r: any) {
  return {
    id: r.id, userId: r.user_id, nombre: r.nombre, categorias: lista(r.categorias), palabrasClave: lista(r.palabras_clave),
    palabrasExcluir: lista(r.palabras_excluir), departamentos: lista(r.departamentos), ciudades: lista(r.ciudades),
    modalidades: lista(r.modalidades), tiposContrato: lista(r.tipos_contrato), tiposEntidad: lista(r.tipos_entidad),
    rangosValor: lista(r.rangos_valor), diasMinRestantes: r.dias_min_restantes, profesionId: r.profesion_id,
    activo: r.activo, desactivadoPorPlan: r.desactivado_por_plan, createdAt: iso(r.created_at),
  };
}

export async function listar(u: Usuario) {
  const filas = await sql`SELECT * FROM secop.perfiles WHERE user_id = ${u.id} ORDER BY created_at DESC, id DESC`;
  return { perfiles: filas.map(aPerfil) };
}

/** Deja los perfiles del usuario de acuerdo con su plan (dentro de la transacción). */
export async function sincronizar(tx: Tx, usuarioId: number, plan: Plan): Promise<void> {
  const filas = await tx`SELECT id, activo, desactivado_por_plan FROM secop.perfiles WHERE user_id = ${usuarioId} ORDER BY created_at, id`;
  const cambios = cambiosPorPlan(filas.map((f) => ({ id: f.id, activo: f.activo, desactivadoPorPlan: f.desactivado_por_plan })), plan);
  for (const c of cambios) {
    await tx`UPDATE secop.perfiles SET activo = ${c.activo}, desactivado_por_plan = ${c.desactivadoPorPlan} WHERE id = ${c.id}`;
  }
}

/** Igual que ProcesoDAO.vigentes: sin cierre o que cerraron hace menos de 2 días. */
export const haceDosDias = () => new Date(Date.now() - 2 * 86_400_000);

export async function procesosVigentes(): Promise<ProcesoMatch[]> {
  const filas = await sql.unsafe(`SELECT ${COLUMNAS_PROCESO} FROM secop.procesos WHERE fecha_cierre IS NULL OR fecha_cierre >= $1`, [haceDosDias()]);
  return filas.map((r) => aProceso(r));
}

export async function crear(u: Usuario, c: Cuerpo) {
  const { perfil, errores } = validarPerfil((c.perfil ?? {}) as Record<string, unknown>);
  if (!perfil) throw malaPeticion(errores[0], { general: errores.join(". ") });
  const plan = planEfectivo(u);
  // El cruce del perfil nuevo se calcula antes de abrir la transacción (es CPU, no base).
  const ahora = Date.now();
  const scores = new Map<string, number>();
  for (const p of await procesosVigentes()) {
    const s = calcularScore(perfil, p, ahora);
    if (s != null) scores.set(p.id, s);
  }
  return await sql.begin(async (tx) => {
    // Bloquea los perfiles del usuario para que dos pestañas no pasen el límite a la vez.
    await tx`SELECT id FROM secop.users WHERE id = ${u.id} FOR UPDATE`;
    if (plan.maxPerfiles != null) {
      const [{ n }] = await tx`SELECT count(*) AS n FROM secop.perfiles WHERE user_id = ${u.id} AND activo`;
      if (n >= plan.maxPerfiles) throw malaPeticion(mensajeLimitePerfiles(plan), { general: mensajeLimitePerfiles(plan) });
    }
    const j = (l: string[]) => JSON.stringify(l);
    const [{ id }] = await tx`
      INSERT INTO secop.perfiles (user_id, nombre, categorias, palabras_clave, palabras_excluir, departamentos, ciudades,
        modalidades, tipos_contrato, tipos_entidad, rangos_valor, dias_min_restantes, profesion_id)
      VALUES (${u.id}, ${perfil.nombre}, ${j(perfil.categorias)}, ${j(perfil.palabrasClave)}, ${j(perfil.palabrasExcluir)},
        ${j(perfil.departamentos)}, ${j(perfil.ciudades)}, ${j(perfil.modalidades)}, ${j(perfil.tiposContrato)},
        ${j(perfil.tiposEntidad)}, ${j(perfil.rangosValor)}, ${perfil.diasMinRestantes}, ${perfil.profesionId})
      RETURNING id`;
    // Lo que ya existe se ve en el panel pero no se avisa como nuevo (notificado = true).
    const filas = [...scores].map(([proceso_id, score]) => ({ perfil_id: id, proceso_id, score, notificado: true }));
    for (let i = 0; i < filas.length; i += 1000) {
      await tx`INSERT INTO secop.coincidencias ${tx(filas.slice(i, i + 1000), "perfil_id", "proceso_id", "score", "notificado")}`;
    }
    return { id, coincidencias: filas.length };
  });
}

export async function eliminar(u: Usuario, c: Cuerpo) {
  const id = entero(c, "id")!;
  await sql.begin(async (tx) => {
    await tx`DELETE FROM secop.perfiles WHERE id = ${id} AND user_id = ${u.id}`;
    // Borrar uno libera cupo: si el plan había apagado otros, se reactiva el siguiente.
    await sincronizar(tx, u.id, planEfectivo(u));
  });
  return { ok: true };
}
