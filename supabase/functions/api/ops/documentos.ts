// "Mis documentos": datos del proponente y archivos. Los archivos van a
// Supabase Storage con URLs firmadas: la app pide permiso (pedirSubida),
// sube el archivo directo por HTTPS y luego confirma; el servidor revisa el
// archivo antes de registrarlo.
import { iso, sql, type Tx } from "../lib/bd.ts";
import { type Cuerpo, entero, fecha, malaPeticion, noEncontrado, texto } from "../lib/http.ts";
import type { Usuario } from "../lib/sesion.ts";
import * as storage from "../lib/storage.ts";
import { type Proponente, vacio, validarProponente } from "../../_shared/dominio/proponente.ts";
import { hoyColombia, tipoDocumento } from "../../_shared/dominio/preparacion.ts";

const COLUMNAS_PROPONENTE = "tipo_persona, tipo_documento, numero_documento, telefono, departamento, ciudad, profesion, "
  + "titulo, universidad, matricula_profesional, anios_experiencia, resumen, registrado_secop, tiene_rup, numero_rup, sigep_actualizada";

// deno-lint-ignore no-explicit-any
function aProponente(r: any): Proponente {
  return {
    tipoPersona: r.tipo_persona, tipoDocumento: r.tipo_documento, numeroDocumento: r.numero_documento, telefono: r.telefono,
    departamento: r.departamento, ciudad: r.ciudad, profesion: r.profesion, titulo: r.titulo, universidad: r.universidad,
    matriculaProfesional: r.matricula_profesional, aniosExperiencia: r.anios_experiencia, resumen: r.resumen,
    registradoSecop: r.registrado_secop, tieneRup: r.tiene_rup, numeroRup: r.numero_rup, sigepActualizada: r.sigep_actualizada,
  };
}

export async function proponente(usuarioId: number): Promise<Proponente> {
  const filas = await sql.unsafe(`SELECT ${COLUMNAS_PROPONENTE} FROM secop.proponentes WHERE user_id = $1`, [usuarioId]);
  return filas.length ? aProponente(filas[0]) : vacio();
}

async function guardarProponente(tx: Tx | typeof sql, usuarioId: number, p: Proponente): Promise<void> {
  await tx`
    INSERT INTO secop.proponentes (user_id, tipo_persona, tipo_documento, numero_documento, telefono, departamento, ciudad,
      profesion, titulo, universidad, matricula_profesional, anios_experiencia, resumen, registrado_secop, tiene_rup,
      numero_rup, sigep_actualizada, actualizado_at)
    VALUES (${usuarioId}, ${p.tipoPersona}, ${p.tipoDocumento}, ${p.numeroDocumento}, ${p.telefono}, ${p.departamento},
      ${p.ciudad}, ${p.profesion}, ${p.titulo}, ${p.universidad}, ${p.matriculaProfesional}, ${p.aniosExperiencia},
      ${p.resumen}, ${p.registradoSecop}, ${p.tieneRup}, ${p.numeroRup}, ${p.sigepActualizada}, now())
    ON CONFLICT (user_id) DO UPDATE SET tipo_persona = EXCLUDED.tipo_persona, tipo_documento = EXCLUDED.tipo_documento,
      numero_documento = EXCLUDED.numero_documento, telefono = EXCLUDED.telefono, departamento = EXCLUDED.departamento,
      ciudad = EXCLUDED.ciudad, profesion = EXCLUDED.profesion, titulo = EXCLUDED.titulo, universidad = EXCLUDED.universidad,
      matricula_profesional = EXCLUDED.matricula_profesional, anios_experiencia = EXCLUDED.anios_experiencia,
      resumen = EXCLUDED.resumen, registrado_secop = EXCLUDED.registrado_secop, tiene_rup = EXCLUDED.tiene_rup,
      numero_rup = EXCLUDED.numero_rup, sigep_actualizada = EXCLUDED.sigep_actualizada, actualizado_at = now()`;
}

export async function obtenerProponente(u: Usuario) {
  return await proponente(u.id);
}

export async function guardarDatos(u: Usuario, c: Cuerpo) {
  const e = (c.proponente ?? {}) as Proponente;
  for (const k of ["numeroDocumento", "telefono", "ciudad", "profesion", "titulo", "universidad", "matriculaProfesional", "numeroRup", "departamento"] as const) {
    if (e[k] != null && (typeof e[k] !== "string" || (e[k] as string).length > 300)) throw malaPeticion("Dato no válido: " + k);
  }
  if (e.resumen != null && (typeof e.resumen !== "string" || e.resumen.length > 5000)) throw malaPeticion("Dato no válido: resumen");
  const { errores, limpio } = validarProponente(e);
  if (Object.keys(errores).length > 0) throw malaPeticion("Revisa los campos marcados en rojo.", errores);
  await guardarProponente(sql, u.id, limpio);
  return limpio;
}

/** Etapa 1 de una postulación: ya tiene cuenta de proveedor. */
export async function marcarRegistrado(tx: Tx, usuarioId: number): Promise<void> {
  await tx`INSERT INTO secop.proponentes (user_id, registrado_secop) VALUES (${usuarioId}, TRUE)
           ON CONFLICT (user_id) DO UPDATE SET registrado_secop = TRUE, actualizado_at = now()`;
}

// ---- Archivos ----

// deno-lint-ignore no-explicit-any
function aDocumento(r: any) {
  return {
    id: r.id, usuarioId: r.user_id, tipo: r.tipo, nombreArchivo: r.nombre_archivo, tamano: r.tamano,
    vence: r.vence, subidoAt: iso(r.subido_at),
  };
}

export async function listar(u: Usuario) {
  const filas = await sql`SELECT id, user_id, tipo, nombre_archivo, tamano, vence, subido_at FROM secop.documentos
                          WHERE user_id = ${u.id} ORDER BY tipo, subido_at`;
  return { documentos: filas.map(aDocumento) };
}

/** Tipos con algún archivo vigente (para el avance de las postulaciones). */
export async function tiposVigentes(tx: Tx | typeof sql, usuarioId: number): Promise<Set<string>> {
  const filas = await tx`SELECT DISTINCT tipo FROM secop.documentos WHERE user_id = ${usuarioId} AND (vence IS NULL OR vence >= ${hoyColombia()})`;
  return new Set(filas.map((f) => f.tipo));
}

function sumarMes(hoy: string): string {
  const d = new Date(hoy + "T12:00:00Z");
  d.setUTCMonth(d.getUTCMonth() + 1);
  return d.toISOString().substring(0, 10);
}

/**
 * Prepara una subida: valida y devuelve una URL firmada para subir el
 * archivo con PUT. `destino` es un documento o la propuesta de una postulación.
 */
export async function pedirSubida(u: Usuario, c: Cuerpo, destino: "documento" | "propuesta", postulacionId: number | null) {
  const nombre = texto(c, "nombre", 255, true)!;
  const ext = storage.extension(nombre);
  if (!storage.TIPOS[ext]) throw malaPeticion("Solo se aceptan archivos PDF, JPG o PNG.");
  const tamano = entero(c, "tamano")!;
  if (tamano <= 0) throw malaPeticion("El archivo está vacío.");
  if (tamano > storage.TAMANO_MAXIMO) throw malaPeticion("El archivo pesa más de 5 MB. Comprímelo o escanéalo con menos resolución.");
  let tipo: string | null = null;
  let vence: string | null = null;
  if (destino === "documento") {
    tipo = texto(c, "tipo", 40, true)!;
    const t = tipoDocumento(tipo);
    if (!t) throw malaPeticion("Tipo de documento no válido.");
    vence = fecha(c, "vence");
    const hoy = hoyColombia();
    if (vence == null && tipo === "SEGURIDAD_SOCIAL") vence = sumarMes(hoy);
    if (vence != null && !t.vence) vence = null;
    if (vence != null && vence < String(Number(hoy.substring(0, 4)) - 1) + hoy.substring(4)) throw malaPeticion("La fecha de vencimiento no es válida.");
  }
  const id = crypto.randomUUID();
  const ruta = `${u.id}/${id}.${ext}`;
  const nombreLimpio = nombre.length > 200 ? nombre.substring(nombre.length - 200) : nombre;
  await sql`INSERT INTO secop.subidas_pendientes (id, usuario_id, destino, tipo, postulacion_id, nombre, ext, vence, ruta)
            VALUES (${id}, ${u.id}, ${destino}, ${tipo}, ${postulacionId}, ${nombreLimpio}, ${ext}, ${vence}, ${ruta})`;
  const { url } = await storage.urlDeSubida(ruta);
  return { subidaId: id, url, metodo: "PUT", contentType: storage.TIPOS[ext] };
}

/** Revisa el archivo subido y lo registra. Devuelve la fila pendiente ya validada. */
// deno-lint-ignore no-explicit-any
export async function validarSubida(u: Usuario, c: Cuerpo, destino: string): Promise<any> {
  const subidaId = texto(c, "subidaId", 40, true)!;
  const [p] = await sql`SELECT * FROM secop.subidas_pendientes WHERE id = ${subidaId} AND usuario_id = ${u.id}
                        AND destino = ${destino} AND expira > now()`;
  if (!p) throw noEncontrado("La subida venció o no existe. Vuelve a subir el archivo.");
  const r = await storage.revisarArchivo(p.ruta, p.ext);
  if (!r.ok) {
    await storage.borrar([p.ruta]);
    await sql`DELETE FROM secop.subidas_pendientes WHERE id = ${subidaId}`;
    throw malaPeticion(r.motivo!);
  }
  return { ...p, tamano: r.tamano };
}

export async function confirmarSubida(u: Usuario, c: Cuerpo) {
  const p = await validarSubida(u, c, "documento");
  const varios = tipoDocumento(p.tipo)!.varios;
  const { documento, viejas } = await sql.begin(async (tx) => {
    let viejas: string[] = [];
    if (!varios) {
      // Un documento único se reemplaza: se borra el anterior en la misma transacción.
      const borrados = await tx`DELETE FROM secop.documentos WHERE user_id = ${u.id} AND tipo = ${p.tipo} RETURNING ruta`;
      viejas = borrados.map((b) => b.ruta).filter((x) => x != null);
    }
    const [d] = await tx`
      INSERT INTO secop.documentos (user_id, tipo, nombre_archivo, tipo_mime, tamano, ruta, vence)
      VALUES (${u.id}, ${p.tipo}, ${p.nombre}, ${storage.TIPOS[p.ext]}, ${p.tamano}, ${p.ruta}, ${p.vence})
      RETURNING id, user_id, tipo, nombre_archivo, tamano, vence, subido_at`;
    await tx`DELETE FROM secop.subidas_pendientes WHERE id = ${p.id}`;
    return { documento: aDocumento(d), viejas };
  });
  await storage.borrar(viejas);
  return documento;
}

/** URL firmada de descarga; los archivos viejos guardados en la base vuelven en base64. */
export async function descargar(u: Usuario, c: Cuerpo) {
  const id = entero(c, "id")!;
  const [d] = await sql`SELECT nombre_archivo, ruta, contenido FROM secop.documentos WHERE id = ${id} AND user_id = ${u.id}`;
  if (!d) throw noEncontrado("El archivo ya no existe.");
  if (d.ruta) return { nombre: d.nombre_archivo, url: await storage.urlDeDescarga(d.ruta, d.nombre_archivo) };
  return { nombre: d.nombre_archivo, base64: btoa(Array.from(d.contenido as Uint8Array, (b) => String.fromCharCode(b)).join("")) };
}

export async function eliminar(u: Usuario, c: Cuerpo) {
  const id = entero(c, "id")!;
  const filas = await sql`DELETE FROM secop.documentos WHERE id = ${id} AND user_id = ${u.id} RETURNING ruta`;
  await storage.borrar(filas.map((f) => f.ruta).filter((x) => x != null));
  return { ok: filas.length > 0 };
}

export async function cambiarVence(u: Usuario, c: Cuerpo) {
  const id = entero(c, "id")!;
  const vence = fecha(c, "vence");
  const [d] = await sql`SELECT tipo FROM secop.documentos WHERE id = ${id} AND user_id = ${u.id}`;
  if (!d) throw noEncontrado("El archivo ya no existe.");
  if (vence != null && !tipoDocumento(d.tipo)?.vence) throw malaPeticion("Ese documento no tiene vencimiento.");
  await sql`UPDATE secop.documentos SET vence = ${vence} WHERE id = ${id} AND user_id = ${u.id}`;
  return { ok: true };
}
