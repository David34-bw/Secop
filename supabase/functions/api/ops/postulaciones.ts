// "Mis postulaciones": las 7 etapas se validan aquí (orden, presupuesto,
// cierre), no solo en la app. Cada acción lee y escribe en una transacción
// con la fila bloqueada, para que dos clics seguidos no se pisen.
import { iso, sql, type Tx } from "../lib/bd.ts";
import { type Cuerpo, entero, malaPeticion, noEncontrado, texto } from "../lib/http.ts";
import type { Usuario } from "../lib/sesion.ts";
import * as storage from "../lib/storage.ts";
import { type Accion, aplicar, avance, type Contexto, ErrorPostulacion, exigir, type Postulacion } from "../../_shared/dominio/etapas.ts";
import { hoyColombia } from "../../_shared/dominio/preparacion.ts";
import { marcarRegistrado, pedirSubida, tiposVigentes, validarSubida } from "./documentos.ts";

const AVISO_TEMPRANO = "Solicitud de información a los Proveedores";

// deno-lint-ignore no-explicit-any
function aPostulacion(r: any) {
  return {
    id: r.id, usuarioId: r.user_id, procesoId: r.proceso_id, entidad: r.entidad, objeto: r.objeto, modalidad: r.modalidad,
    valor: r.valor, fechaCierre: iso(r.fecha_cierre), url: r.url, revisado: r.revisado, pliegoLeido: r.pliego_leido,
    notas: r.notas, requisitosExtra: JSON.parse(r.requisitos_extra ?? "[]"), valorOfertado: r.valor_ofertado,
    propuestaArchivo: r.propuesta_nombre, presentadaAt: iso(r.presentada_at), resultado: r.resultado,
    plazoSubsanar: r.plazo_subsanar, desistida: r.desistida, createdAt: iso(r.created_at),
  };
}

const COLUMNAS = "id, user_id, proceso_id, entidad, objeto, modalidad, valor, fecha_cierre, url, revisado, pliego_leido, notas, "
  + "requisitos_extra, valor_ofertado, propuesta_nombre, presentada_at, resultado, plazo_subsanar, desistida, created_at";

async function contexto(tx: Tx | typeof sql, usuarioId: number): Promise<Contexto> {
  const [p] = await tx`SELECT registrado_secop FROM secop.proponentes WHERE user_id = ${usuarioId}`;
  return { registradoSecop: p?.registrado_secop === true, tiposVigentes: await tiposVigentes(tx, usuarioId) };
}

function conAvance(p: ReturnType<typeof aPostulacion>, c: Contexto) {
  return { postulacion: p, avance: avance(p as Postulacion, c) };
}

export async function listar(u: Usuario) {
  const filas = await sql.unsafe(`SELECT ${COLUMNAS} FROM secop.postulaciones WHERE user_id = $1 ORDER BY desistida, fecha_cierre`, [u.id]);
  const c = await contexto(sql, u.id);
  return { postulaciones: filas.map((f) => conAvance(aPostulacion(f), c)) };
}

/** proceso -> id de la postulación (para "Ver mi postulación" en las tarjetas). */
export async function idsPorProceso(u: Usuario) {
  const filas = await sql`SELECT proceso_id, id FROM secop.postulaciones WHERE user_id = ${u.id}`;
  return Object.fromEntries(filas.map((f) => [f.proceso_id, f.id]));
}

export async function crear(u: Usuario, c: Cuerpo) {
  const procesoId = texto(c, "procesoId", 100, true)!;
  const [existente] = await sql`SELECT id FROM secop.postulaciones WHERE user_id = ${u.id} AND proceso_id = ${procesoId}`;
  if (existente) return { id: existente.id };
  const [p] = await sql`SELECT * FROM secop.procesos WHERE id_del_proceso = ${procesoId}`;
  if (!p) throw noEncontrado("No se encontró el proceso.");
  if (p.modalidad === AVISO_TEMPRANO) throw malaPeticion("Este proceso es un sondeo de mercado: aún no recibe ofertas.");
  if (p.fecha_cierre != null && new Date(p.fecha_cierre).getTime() < Date.now()) throw malaPeticion("Este proceso ya cerró.");
  const [r] = await sql`
    INSERT INTO secop.postulaciones (user_id, proceso_id, entidad, objeto, modalidad, valor, fecha_cierre, url)
    VALUES (${u.id}, ${procesoId}, ${p.entidad}, ${p.objeto}, ${p.modalidad}, ${p.valor}, ${p.fecha_cierre}, ${p.url})
    ON CONFLICT (user_id, proceso_id) DO UPDATE SET actualizado_at = secop.postulaciones.actualizado_at
    RETURNING id`;
  return { id: r.id };
}

export async function obtener(u: Usuario, c: Cuerpo) {
  const id = entero(c, "id")!;
  // Pone al día los datos del proceso (una adenda puede mover el cierre).
  await sql`UPDATE secop.postulaciones po SET entidad = p.entidad, objeto = p.objeto, modalidad = p.modalidad,
              valor = p.valor, fecha_cierre = p.fecha_cierre, url = p.url
            FROM secop.procesos p WHERE p.id_del_proceso = po.proceso_id AND po.id = ${id} AND po.user_id = ${u.id}`;
  const filas = await sql.unsafe(`SELECT ${COLUMNAS} FROM secop.postulaciones WHERE id = $1 AND user_id = $2`, [id, u.id]);
  if (filas.length === 0) throw noEncontrado("No se encontró la postulación.");
  return conAvance(aPostulacion(filas[0]), await contexto(sql, u.id));
}

/** Arma la acción revisando el tipo de cada dato que manda la app. */
function leerAccion(accion: string, c: Cuerpo): Accion {
  const bool = () => {
    if (typeof c.valor !== "boolean") throw malaPeticion("valor debe ser verdadero o falso.");
    return c.valor;
  };
  switch (accion) {
    case "revisar": return { accion, valor: bool() };
    case "presentar": return { accion, valor: bool() };
    case "desistir": return { accion, valor: bool() };
    case "pliego": return { accion, valor: bool(), notas: texto(c, "notas", 8000) };
    case "notas": return { accion, notas: texto(c, "notas", 8000) };
    case "requisitoAgregar": return { accion, texto: texto(c, "texto", 400) ?? "" };
    case "requisitoAlternar": return { accion, indice: entero(c, "indice")! };
    case "requisitoQuitar": return { accion, indice: entero(c, "indice")! };
    case "propuesta": return { accion, valor: entero(c, "valor", false) };
    case "resultado": {
      const r = texto(c, "resultado", 20);
      if (r != null && r !== "GANADA" && r !== "NO_GANADA" && r !== "SUBSANAR") throw malaPeticion("Resultado no válido.");
      return { accion, resultado: r as "GANADA" | "NO_GANADA" | "SUBSANAR" | null, plazo: texto(c, "plazo", 10) };
    }
  }
  throw malaPeticion("Acción no válida.");
}

const ACCIONES = new Set(["registro", "revisar", "pliego", "notas", "requisitoAgregar", "requisitoAlternar",
  "requisitoQuitar", "propuesta", "presentar", "resultado", "desistir"]);

export async function actualizar(u: Usuario, c: Cuerpo) {
  const id = entero(c, "id")!;
  const accion = texto(c, "accion", 30, true)!;
  if (!ACCIONES.has(accion)) throw malaPeticion("Acción no válida.");
  return await sql.begin(async (tx) => {
    const filas = await tx.unsafe(`SELECT ${COLUMNAS} FROM secop.postulaciones WHERE id = $1 AND user_id = $2 FOR UPDATE`, [id, u.id]);
    if (filas.length === 0) throw noEncontrado("No se encontró la postulación.");
    const actual = aPostulacion(filas[0]);
    if (accion === "registro") {
      await marcarRegistrado(tx, u.id);
      return conAvance(actual, await contexto(tx, u.id));
    }
    const ctx = await contexto(tx, u.id);
    let cambios;
    try {
      cambios = aplicar(leerAccion(accion, c), actual as Postulacion, ctx, hoyColombia(), Date.now());
    } catch (e) {
      if (e instanceof ErrorPostulacion) throw malaPeticion(e.message);
      throw e;
    }
    const columnas: Record<string, unknown> = {};
    if ("revisado" in cambios) columnas.revisado = cambios.revisado;
    if ("pliegoLeido" in cambios) columnas.pliego_leido = cambios.pliegoLeido;
    if ("notas" in cambios) columnas.notas = cambios.notas;
    if ("requisitosExtra" in cambios) columnas.requisitos_extra = JSON.stringify(cambios.requisitosExtra);
    if ("valorOfertado" in cambios) columnas.valor_ofertado = cambios.valorOfertado;
    if ("presentadaAt" in cambios) columnas.presentada_at = cambios.presentadaAt == null ? null : new Date(cambios.presentadaAt);
    if ("resultado" in cambios) columnas.resultado = cambios.resultado;
    if ("plazoSubsanar" in cambios) columnas.plazo_subsanar = cambios.plazoSubsanar;
    if ("desistida" in cambios) columnas.desistida = cambios.desistida;
    columnas.actualizado_at = new Date();
    const r = await tx.unsafe(
      `UPDATE secop.postulaciones SET ${Object.keys(columnas).map((k, i) => `${k} = $${i + 3}`).join(", ")}
       WHERE id = $1 AND user_id = $2 RETURNING ${COLUMNAS}`,
      // Los nombres de columna salen de la lista fija de arriba, nunca del cliente.
      [id, u.id, ...Object.values(columnas)] as never[],
    );
    return conAvance(aPostulacion(r[0]), ctx);
  });
}

export async function eliminar(u: Usuario, c: Cuerpo) {
  const id = entero(c, "id")!;
  const filas = await sql`DELETE FROM secop.postulaciones WHERE id = ${id} AND user_id = ${u.id} RETURNING propuesta_ruta`;
  await storage.borrar(filas.map((f) => f.propuesta_ruta).filter((x) => x != null));
  return { ok: filas.length > 0 };
}

// ---- Archivo de la propuesta económica (etapa 5) ----

async function exigirEtapaPropuesta(u: Usuario, id: number) {
  const filas = await sql.unsafe(`SELECT ${COLUMNAS} FROM secop.postulaciones WHERE id = $1 AND user_id = $2`, [id, u.id]);
  if (filas.length === 0) throw noEncontrado("No se encontró la postulación.");
  try {
    exigir("PROPUESTA", aPostulacion(filas[0]) as Postulacion, await contexto(sql, u.id));
  } catch (e) {
    if (e instanceof ErrorPostulacion) throw malaPeticion(e.message);
    throw e;
  }
}

export async function pedirSubidaPropuesta(u: Usuario, c: Cuerpo) {
  const id = entero(c, "id")!;
  await exigirEtapaPropuesta(u, id);
  return await pedirSubida(u, c, "propuesta", id);
}

export async function confirmarSubidaPropuesta(u: Usuario, c: Cuerpo) {
  const p = await validarSubida(u, c, "propuesta");
  await exigirEtapaPropuesta(u, p.postulacion_id);
  const vieja = await sql.begin(async (tx) => {
    const [antes] = await tx`SELECT propuesta_ruta FROM secop.postulaciones WHERE id = ${p.postulacion_id} AND user_id = ${u.id} FOR UPDATE`;
    await tx`UPDATE secop.postulaciones SET propuesta_nombre = ${p.nombre}, propuesta_ruta = ${p.ruta},
               propuesta_contenido = NULL, actualizado_at = now() WHERE id = ${p.postulacion_id} AND user_id = ${u.id}`;
    await tx`DELETE FROM secop.subidas_pendientes WHERE id = ${p.id}`;
    return antes?.propuesta_ruta ?? null;
  });
  if (vieja) await storage.borrar([vieja]);
  return await obtener(u, { id: p.postulacion_id });
}

export async function descargarPropuesta(u: Usuario, c: Cuerpo) {
  const id = entero(c, "id")!;
  const [d] = await sql`SELECT propuesta_nombre, propuesta_ruta, propuesta_contenido FROM secop.postulaciones WHERE id = ${id} AND user_id = ${u.id}`;
  if (!d || !d.propuesta_nombre) throw noEncontrado("No hay archivo de propuesta.");
  if (d.propuesta_ruta) return { nombre: d.propuesta_nombre, url: await storage.urlDeDescarga(d.propuesta_ruta, d.propuesta_nombre) };
  return { nombre: d.propuesta_nombre, base64: btoa(Array.from(d.propuesta_contenido as Uint8Array, (b) => String.fromCharCode(b)).join("")) };
}
