// Actualización diaria en el servidor (antes la hacía cada app de escritorio):
// descargar SECOP, cruzar perfiles y ajustar planes. pg_cron llama a
// job.ingesta una vez al día; cada paso procesa un lote y se vuelve a llamar
// para el siguiente, para no pasar el límite de CPU por petición.
import { sql } from "../lib/bd.ts";
import { type Cuerpo, entero, prohibido } from "../lib/http.ts";
import { desdeHace, normalizarFila, PAGINA, urlPagina } from "../../_shared/dominio/ingesta.ts";
import { calcularScore } from "../../_shared/dominio/matching.ts";
import { cambiosPorPlan, planEfectivo } from "../../_shared/dominio/plan.ts";
import { aUsuario, COLUMNAS_USUARIO } from "../lib/sesion.ts";
import { borrar } from "../lib/storage.ts";
import { procesosVigentes } from "./perfiles.ts";

/** Perfiles que se cruzan en cada llamada (cada uno contra todos los procesos vigentes). */
const PERFILES_POR_LLAMADA = 10;

/** El job solo lo puede llamar quien tenga el secreto SECOP_JOB_SECRET (pg_cron o el propio job). */
export function exigirSecreto(req: Request): void {
  const esperado = Deno.env.get("SECOP_JOB_SECRET") ?? "";
  const recibido = req.headers.get("x-secop-job") ?? "";
  let distinto = esperado.length === 0 || esperado.length !== recibido.length ? 1 : 0;
  for (let i = 0; i < Math.min(esperado.length, recibido.length); i++) distinto |= esperado.charCodeAt(i) ^ recibido.charCodeAt(i);
  if (distinto !== 0) throw prohibido();
}

/** Llama al siguiente paso sin esperar la respuesta. */
export function lanzar(operacion: string, cuerpo: Record<string, unknown>): void {
  const publicas = JSON.parse(Deno.env.get("SUPABASE_PUBLISHABLE_KEYS") ?? "{}");
  const p = fetch(`${Deno.env.get("SUPABASE_URL")}/functions/v1/api/${operacion}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "apikey": publicas["default"] ?? Deno.env.get("SUPABASE_ANON_KEY") ?? "",
      "x-secop-job": Deno.env.get("SECOP_JOB_SECRET") ?? "",
    },
    body: JSON.stringify(cuerpo),
  }).then((r) => r.body?.cancel()).catch((e) => console.error("[job] no se pudo lanzar " + operacion, e));
  // @ts-ignore EdgeRuntime existe en Supabase Edge Functions.
  if (typeof EdgeRuntime !== "undefined") EdgeRuntime.waitUntil(p);
}

const num = (c: Cuerpo, k: string) => entero(c, k, false) ?? 0;

export async function ingesta(c: Cuerpo) {
  const offset = num(c, "offset");
  const dias = Math.min(Math.max(entero(c, "dias", false) ?? 5, 1), 30);
  let leidos = num(c, "leidos"), nuevos = num(c, "nuevos"), actualizados = num(c, "actualizados");
  try {
    const headers: Record<string, string> = { Accept: "application/json" };
    const token = Deno.env.get("SOCRATA_APP_TOKEN");
    if (token) headers["X-App-Token"] = token;
    const res = await fetch(urlPagina(desdeHace(dias), offset), { headers, signal: AbortSignal.timeout(60_000) });
    if (res.status !== 200) throw new Error("API de SECOP respondió " + res.status);
    const crudos = await res.json() as Record<string, unknown>[];

    // Si la fuente repite un id en la página, queda la última versión.
    const porId = new Map<string, ReturnType<typeof normalizarFila>>();
    for (const p of crudos) {
      const f = normalizarFila(p);
      if (f.id != null) porId.set(f.id, f);
    }
    if (porId.size > 0) {
      const filas = [...porId.values()].map((f) => ({
        id_del_proceso: f.id, entidad: f.entidad, nombre: f.nombre, objeto: f.objeto, modalidad: f.modalidad,
        categoria: f.categoria, tipo_contrato: f.tipoContrato, valor: f.valor, departamento: f.departamento, ciudad: f.ciudad,
        estado_apertura: f.estadoApertura, fecha_publicacion: f.fechaPublicacion, fecha_cierre: f.fechaCierre, url: f.url,
        ordenentidad: f.ordenEntidad, estado_procedimiento: f.estadoProcedimiento, respuestas: f.respuestas, raw: f.raw,
      }));
      // Un solo upsert por página. xmax = 0 indica que la fila es nueva.
      const r = await sql`
        INSERT INTO secop.procesos ${sql(filas)}
        ON CONFLICT (id_del_proceso) DO UPDATE SET estado_apertura = EXCLUDED.estado_apertura,
          fecha_cierre = EXCLUDED.fecha_cierre, valor = EXCLUDED.valor, ordenentidad = EXCLUDED.ordenentidad,
          nombre = EXCLUDED.nombre, estado_procedimiento = EXCLUDED.estado_procedimiento,
          respuestas = EXCLUDED.respuestas, raw = EXCLUDED.raw, actualizado_at = now()
        RETURNING (xmax = 0) AS nuevo`;
      const n = r.filter((x) => x.nuevo).length;
      nuevos += n;
      actualizados += r.length - n;
    }
    leidos += crudos.length;

    if (crudos.length === PAGINA) {
      lanzar("job.ingesta", { offset: offset + PAGINA, dias, leidos, nuevos, actualizados });
      return { paso: "ingesta", offset, leidos };
    }
    // Si no entra un proceso nuevo en varios días, algo está roto.
    const [{ ultimo }] = await sql`SELECT max(ingresado_at) AS ultimo FROM secop.procesos`;
    const rezago = ultimo == null ? 0 : (Date.now() - new Date(ultimo).getTime()) / 86_400_000;
    if (rezago >= 3) throw new Error(`No entra un proceso nuevo hace ${rezago.toFixed(1)} días. Revisa la API de Socrata.`);
    await sql`INSERT INTO secop.ingesta_log (leidos, nuevos, actualizados, exito) VALUES (${leidos}, ${nuevos}, ${actualizados}, TRUE)`;
    lanzar("job.cruce", { desdePerfil: 0, perfiles: 0, nuevas: 0, quitadas: 0 });
    return { paso: "ingesta", terminado: true, leidos, nuevos, actualizados };
  } catch (e) {
    const mensaje = String((e as Error)?.message ?? e).substring(0, 4000);
    await sql`INSERT INTO secop.ingesta_log (leidos, nuevos, actualizados, exito, error)
              VALUES (${leidos}, ${nuevos}, ${actualizados}, FALSE, ${mensaje})`;
    throw e;
  }
}

export async function cruce(c: Cuerpo) {
  const desde = num(c, "desdePerfil");
  let totalPerfiles = num(c, "perfiles"), nuevas = num(c, "nuevas"), quitadas = num(c, "quitadas");
  const vigentes = await procesosVigentes();
  try {
    const perfiles = await sql`SELECT * FROM secop.perfiles WHERE activo AND id > ${desde} ORDER BY id LIMIT ${PERFILES_POR_LLAMADA}`;
    const ahora = Date.now();
    for (const pf of perfiles) {
      const perfil = {
        categorias: JSON.parse(pf.categorias), palabrasClave: JSON.parse(pf.palabras_clave),
        palabrasExcluir: JSON.parse(pf.palabras_excluir), departamentos: JSON.parse(pf.departamentos),
        ciudades: JSON.parse(pf.ciudades), modalidades: JSON.parse(pf.modalidades), tiposContrato: JSON.parse(pf.tipos_contrato),
        tiposEntidad: JSON.parse(pf.tipos_entidad), rangosValor: JSON.parse(pf.rangos_valor), diasMinRestantes: pf.dias_min_restantes,
      };
      const calculadas = new Map<string, number>();
      for (const p of vigentes) {
        const s = calcularScore(perfil, p, ahora);
        if (s != null) calculadas.set(p.id, s);
      }
      await sql.begin(async (tx) => {
        const guardadas = new Map((await tx`SELECT proceso_id, score FROM secop.coincidencias WHERE perfil_id = ${pf.id}`)
          .map((g) => [g.proceso_id as string, g.score as number]));
        const insertar = [...calculadas].filter(([id]) => !guardadas.has(id))
          .map(([proceso_id, score]) => ({ perfil_id: pf.id, proceso_id, score, notificado: false }));
        for (let i = 0; i < insertar.length; i += 1000) {
          await tx`INSERT INTO secop.coincidencias ${tx(insertar.slice(i, i + 1000), "perfil_id", "proceso_id", "score", "notificado")}`;
        }
        for (const [id, score] of calculadas) {
          const previo = guardadas.get(id);
          if (previo != null && previo !== score) {
            await tx`UPDATE secop.coincidencias SET score = ${score} WHERE perfil_id = ${pf.id} AND proceso_id = ${id}`;
          }
        }
        // Vigentes que ya no pasan las reglas se quitan; las cerradas quedan como historial.
        const quitar = vigentes.filter((p) => guardadas.has(p.id) && !calculadas.has(p.id)).map((p) => p.id);
        if (quitar.length > 0) {
          const r = await tx`DELETE FROM secop.coincidencias WHERE perfil_id = ${pf.id} AND proceso_id = ANY(${quitar})`;
          quitadas += r.count;
        }
        nuevas += insertar.length;
      });
      totalPerfiles++;
    }
    if (perfiles.length === PERFILES_POR_LLAMADA) {
      lanzar("job.cruce", { desdePerfil: perfiles[perfiles.length - 1].id, perfiles: totalPerfiles, nuevas, quitadas });
      return { paso: "cruce", hasta: perfiles[perfiles.length - 1].id };
    }
    await sql`INSERT INTO secop.coincidencias_log (perfiles, procesos, nuevas, quitadas, exito)
              VALUES (${totalPerfiles}, ${vigentes.length}, ${nuevas}, ${quitadas}, TRUE)`;
    lanzar("job.planes", {});
    return { paso: "cruce", terminado: true, perfiles: totalPerfiles, nuevas, quitadas };
  } catch (e) {
    const mensaje = String((e as Error)?.message ?? e).substring(0, 4000);
    await sql`INSERT INTO secop.coincidencias_log (perfiles, procesos, nuevas, quitadas, exito, error)
              VALUES (${totalPerfiles}, ${vigentes.length}, ${nuevas}, ${quitadas}, FALSE, ${mensaje})`;
    throw e;
  }
}

/** Gracias vencidas pasan a Gratis y cada cuenta queda con los perfiles que su plan permite. */
export async function planes() {
  const vencidas = await sql`UPDATE secop.users SET plan = 'gratis', gracia_hasta = NULL
                             WHERE gracia_hasta IS NOT NULL AND gracia_hasta < now() AND plan <> 'gratis'`;
  let cambios = 0;
  for (const f of await sql.unsafe(`SELECT ${COLUMNAS_USUARIO} FROM secop.users ORDER BY id`)) {
    const u = aUsuario(f);
    await sql.begin(async (tx) => {
      const pf = await tx`SELECT id, activo, desactivado_por_plan FROM secop.perfiles WHERE user_id = ${u.id} ORDER BY created_at, id FOR UPDATE`;
      const lista = cambiosPorPlan(pf.map((p) => ({ id: p.id, activo: p.activo, desactivadoPorPlan: p.desactivado_por_plan })), planEfectivo(u));
      for (const x of lista) await tx`UPDATE secop.perfiles SET activo = ${x.activo}, desactivado_por_plan = ${x.desactivadoPorPlan} WHERE id = ${x.id}`;
      cambios += lista.length;
    });
  }
  // Limpieza: sesiones vencidas, intentos viejos y subidas que nunca se confirmaron.
  await sql`DELETE FROM secop.sesiones WHERE expira < now()`;
  await sql`DELETE FROM secop.intentos_login WHERE creado < now() - interval '1 day'`;
  const huerfanas = await sql`DELETE FROM secop.subidas_pendientes WHERE expira < now() RETURNING ruta`;
  await borrar(huerfanas.map((h) => h.ruta));
  return { paso: "planes", terminado: true, graciasVencidas: vencidas.count, perfilesCambiados: cambios };
}
