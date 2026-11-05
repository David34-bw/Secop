// Archivos en Supabase Storage (bucket privado "documentos"). La app nunca
// tiene la clave secreta: el servidor le da URLs firmadas de corta duración
// para subir o bajar un archivo puntual.
import { createClient } from "@supabase/supabase-js";

export const BUCKET = "documentos";
export const TAMANO_MAXIMO = 5 * 1024 * 1024;
export const TIPOS: Record<string, string> = { pdf: "application/pdf", jpg: "image/jpeg", jpeg: "image/jpeg", png: "image/png" };

function claveSecreta(): string {
  const nuevas = Deno.env.get("SUPABASE_SECRET_KEYS");
  if (nuevas) {
    const k = JSON.parse(nuevas)["default"];
    if (k) return k;
  }
  const legado = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!legado) throw new Error("Falta la clave secreta del proyecto");
  return legado;
}

const admin = createClient(Deno.env.get("SUPABASE_URL")!, claveSecreta(), { auth: { persistSession: false } });

export function extension(nombre: string): string {
  const i = nombre.lastIndexOf(".");
  return i < 0 ? "" : nombre.substring(i + 1).toLowerCase();
}

/** URL para que la app suba el archivo con PUT (válida 2 horas, de un solo uso). */
export async function urlDeSubida(ruta: string): Promise<{ url: string; token: string }> {
  const { data, error } = await admin.storage.from(BUCKET).createSignedUploadUrl(ruta);
  if (error || !data) throw new Error("No se pudo preparar la subida: " + error?.message);
  return { url: data.signedUrl, token: data.token };
}

/** URL para descargar (válida 60 segundos). */
export async function urlDeDescarga(ruta: string, nombre: string): Promise<string> {
  const { data, error } = await admin.storage.from(BUCKET).createSignedUrl(ruta, 60, { download: nombre });
  if (error || !data) throw new Error("No se pudo preparar la descarga: " + error?.message);
  return data.signedUrl;
}

/** Revisa que el archivo exista, su tamaño y que sea lo que dice ser. */
export async function revisarArchivo(ruta: string, ext: string): Promise<{ ok: boolean; tamano: number; motivo?: string }> {
  const { data, error } = await admin.storage.from(BUCKET).download(ruta);
  if (error || !data) return { ok: false, tamano: 0, motivo: "El archivo no llegó. Intenta subirlo de nuevo." };
  if (data.size === 0) return { ok: false, tamano: 0, motivo: "El archivo está vacío." };
  if (data.size > TAMANO_MAXIMO) return { ok: false, tamano: data.size, motivo: "El archivo pesa más de 5 MB." };
  const d = new Uint8Array(await data.slice(0, 8).arrayBuffer());
  const ok = ext === "pdf" ? d[0] === 0x25 && d[1] === 0x50 && d[2] === 0x44 && d[3] === 0x46
    : ext === "png" ? d[0] === 0x89 && d[1] === 0x50 && d[2] === 0x4e && d[3] === 0x47
    : d[0] === 0xff && d[1] === 0xd8;
  return ok ? { ok, tamano: data.size } : { ok, tamano: data.size, motivo: "El archivo no es un " + ext.toUpperCase() + " válido." };
}

export async function borrar(rutas: string[]): Promise<void> {
  if (rutas.length > 0) await admin.storage.from(BUCKET).remove(rutas);
}
