// Foto de perfil. La app ya la manda recortada y reducida (256x256, JPEG o
// PNG, unos 20 KB), así que va dentro de la base y viaja en base64: no hace
// falta Storage ni URLs firmadas. Tabla aparte para que leer usuarios no
// arrastre las fotos.
import { sql } from "../lib/bd.ts";
import { type Cuerpo, malaPeticion } from "../lib/http.ts";
import type { Usuario } from "../lib/sesion.ts";

/** Tope de la foto ya preparada por la app (cabe de sobra en el límite de 256 KB de la petición). */
const MAX_FOTO = 150 * 1024;

function aBase64(b: Uint8Array): string {
  let s = "";
  for (let i = 0; i < b.length; i += 0x8000) s += String.fromCharCode(...b.subarray(i, i + 0x8000));
  return btoa(s);
}

function deBase64(texto: string): Uint8Array {
  const s = atob(texto);
  const b = new Uint8Array(s.length);
  for (let i = 0; i < s.length; i++) b[i] = s.charCodeAt(i);
  return b;
}

/** Tipo según los primeros bytes (no se confía en lo que diga la app). */
export function tipoImagen(b: Uint8Array): "image/jpeg" | "image/png" | null {
  if (b.length > 3 && b[0] === 0xff && b[1] === 0xd8 && b[2] === 0xff) return "image/jpeg";
  if (b.length > 8 && b[0] === 0x89 && b[1] === 0x50 && b[2] === 0x4e && b[3] === 0x47) return "image/png";
  return null;
}

export async function foto(u: Usuario) {
  const [f] = await sql`SELECT contenido, tipo FROM secop.fotos_perfil WHERE usuario_id = ${u.id}`;
  if (!f) return { tipo: null, base64: null };
  return { tipo: f.tipo, base64: aBase64(f.contenido as Uint8Array) };
}

export async function cambiarFoto(u: Usuario, c: Cuerpo) {
  const texto = c.base64;
  if (typeof texto !== "string" || texto.length === 0) throw malaPeticion("Falta la foto.");
  if (texto.length > Math.ceil(MAX_FOTO / 3) * 4 + 4) throw malaPeticion("La foto es demasiado grande.");
  let b: Uint8Array;
  try {
    b = deBase64(texto);
  } catch {
    throw malaPeticion("La foto no se pudo leer.");
  }
  const tipo = tipoImagen(b);
  if (!tipo) throw malaPeticion("La foto debe ser JPG o PNG.");
  if (b.length > MAX_FOTO) throw malaPeticion("La foto es demasiado grande.");
  await sql`INSERT INTO secop.fotos_perfil (usuario_id, contenido, tipo, actualizado_at)
            VALUES (${u.id}, ${b}, ${tipo}, now())
            ON CONFLICT (usuario_id) DO UPDATE SET contenido = EXCLUDED.contenido, tipo = EXCLUDED.tipo,
              actualizado_at = EXCLUDED.actualizado_at`;
  return { ok: true };
}

export async function quitarFoto(u: Usuario) {
  await sql`DELETE FROM secop.fotos_perfil WHERE usuario_id = ${u.id}`;
  return { ok: true };
}
