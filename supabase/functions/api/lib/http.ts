// Respuestas y errores de la API. Los errores internos nunca devuelven
// detalles de la base al cliente: se registran y se responde un mensaje genérico.

export class ErrorApi extends Error {
  constructor(public readonly estado: number, public readonly codigo: string, mensaje: string, public readonly campos?: Record<string, string>) {
    super(mensaje);
  }
}

export const malaPeticion = (mensaje: string, campos?: Record<string, string>) => new ErrorApi(400, "invalido", mensaje, campos);
export const noAutorizado = (mensaje = "Inicia sesión de nuevo.") => new ErrorApi(401, "sin_sesion", mensaje);
export const prohibido = (mensaje = "No tienes permiso para esto.") => new ErrorApi(403, "prohibido", mensaje);
export const noEncontrado = (mensaje = "No se encontró.") => new ErrorApi(404, "no_encontrado", mensaje);
export const demasiados = (mensaje: string) => new ErrorApi(429, "demasiados_intentos", mensaje);

export function json(datos: unknown, estado = 200): Response {
  return new Response(JSON.stringify(datos), {
    status: estado,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
}

export function error(e: unknown): Response {
  if (e instanceof ErrorApi) {
    return json({ error: { codigo: e.codigo, mensaje: e.message, campos: e.campos ?? null } }, e.estado);
  }
  console.error("[api] error interno:", e);
  return json({ error: { codigo: "interno", mensaje: "Ocurrió un error en el servidor. Intenta de nuevo.", campos: null } }, 500);
}

// ---- Lectura segura del cuerpo ----

export type Cuerpo = Record<string, unknown>;

export function texto(c: Cuerpo, campo: string, max: number, obligatorio = false): string | null {
  const v = c[campo];
  if (v == null || v === "") {
    if (obligatorio) throw malaPeticion("Falta " + campo + ".");
    return null;
  }
  if (typeof v !== "string") throw malaPeticion(campo + " debe ser texto.");
  if (v.length > max) throw malaPeticion(campo + " es demasiado largo.");
  return v;
}

export function entero(c: Cuerpo, campo: string, obligatorio = true): number | null {
  const v = c[campo];
  if (v == null) {
    if (obligatorio) throw malaPeticion("Falta " + campo + ".");
    return null;
  }
  if (typeof v !== "number" || !Number.isSafeInteger(v)) throw malaPeticion(campo + " debe ser un número entero.");
  return v;
}

export function booleano(c: Cuerpo, campo: string): boolean {
  const v = c[campo];
  if (typeof v !== "boolean") throw malaPeticion(campo + " debe ser verdadero o falso.");
  return v;
}

export function fecha(c: Cuerpo, campo: string): string | null {
  const v = texto(c, campo, 10);
  if (v == null) return null;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(v) || Number.isNaN(Date.parse(v))) throw malaPeticion(campo + " debe ser una fecha AAAA-MM-DD.");
  return v;
}
