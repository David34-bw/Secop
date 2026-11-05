// Normalización de texto (port de co.secopradar.modelo.util.Texto).
// Módulo puro: sin Deno ni npm, para probarlo también con Node.

/**
 * Texto comparable: minúsculas, sin tildes (ñ -> n), sin signos y con un
 * solo espacio entre palabras. Igual que Texto.normalizar en Java.
 */
export function normalizar(texto: string | null | undefined): string {
  if (texto == null) return "";
  const sinTildes = texto.normalize("NFD").replace(/\p{M}+/gu, "");
  return sinTildes.toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();
}

export function palabras(texto: string | null | undefined): string[] {
  const limpio = normalizar(texto);
  return limpio === "" ? [] : limpio.split(" ");
}

export function recortar(s: string | null | undefined, max: number): string | null {
  if (s == null) return null;
  const t = s.trim();
  return t.length > max ? t.substring(0, max) : t;
}
