// Sesiones propias: token aleatorio y opaco que la app guarda en memoria y
// manda en la cabecera x-secop-sesion. En la base solo se guarda su hash
// SHA-256, así que una copia de la tabla no sirve para entrar.
import { iso, sql, type Tx } from "./bd.ts";
import { noAutorizado, prohibido } from "./http.ts";
import { planEfectivo } from "../../_shared/dominio/plan.ts";

export const DURACION_SESION_MS = 7 * 24 * 3_600_000;

export interface Usuario {
  id: number;
  email: string;
  nombre: string | null;
  rol: string;
  plan: string;
  pruebaHasta: string | null;
  graciaHasta: string | null;
  prepagoPlan: string | null;
  prepagoHasta: string | null;
  alertasActivas: boolean;
  createdAt: string | null;
}

export const COLUMNAS_USUARIO = "id, email, nombre, rol, plan, prueba_hasta, gracia_hasta, prepago_plan, prepago_hasta, alertas_activas, created_at";

// deno-lint-ignore no-explicit-any
export function aUsuario(r: any): Usuario {
  return {
    id: r.id, email: r.email, nombre: r.nombre, rol: r.rol, plan: r.plan,
    pruebaHasta: iso(r.prueba_hasta), graciaHasta: iso(r.gracia_hasta), prepagoPlan: r.prepago_plan,
    prepagoHasta: iso(r.prepago_hasta), alertasActivas: r.alertas_activas, createdAt: iso(r.created_at),
  };
}

/** Usuario más su plan efectivo calculado en el servidor. */
export function conPlan(u: Usuario) {
  return { ...u, planEfectivo: planEfectivo(u) };
}

function base64url(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes)).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

async function hash(token: string): Promise<string> {
  const d = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(token)));
  return Array.from(d, (b) => b.toString(16).padStart(2, "0")).join("");
}

/** Crea una sesión y devuelve el token (solo esta vez se ve en claro). */
export async function crearSesion(tx: Tx | typeof sql, usuarioId: number): Promise<{ token: string; expira: string }> {
  const token = base64url(crypto.getRandomValues(new Uint8Array(32)));
  const expira = new Date(Date.now() + DURACION_SESION_MS);
  await tx`INSERT INTO secop.sesiones (token_hash, usuario_id, expira) VALUES (${await hash(token)}, ${usuarioId}, ${expira})`;
  return { token, expira: expira.toISOString() };
}

export async function cerrarSesion(token: string): Promise<void> {
  await sql`DELETE FROM secop.sesiones WHERE token_hash = ${await hash(token)}`;
}

/** Usuario dueño del token, o error 401. El usuario siempre sale del token, nunca del cuerpo. */
export async function usuarioDeSesion(req: Request): Promise<{ usuario: Usuario; token: string }> {
  const token = req.headers.get("x-secop-sesion");
  if (!token || token.length > 100) throw noAutorizado();
  const h = await hash(token);
  const filas = await sql.unsafe(
    `SELECT u.${COLUMNAS_USUARIO.split(", ").join(", u.")}, s.ultimo_uso FROM secop.sesiones s
     JOIN secop.users u ON u.id = s.usuario_id WHERE s.token_hash = $1 AND s.expira > now()`,
    [h],
  );
  if (filas.length === 0) throw noAutorizado("Tu sesión venció. Inicia sesión de nuevo.");
  // Marca de uso como máximo cada 10 minutos para no escribir en cada petición.
  if (Date.now() - new Date(filas[0].ultimo_uso).getTime() > 600_000) {
    await sql`UPDATE secop.sesiones SET ultimo_uso = now() WHERE token_hash = ${h}`;
  }
  return { usuario: aUsuario(filas[0]), token };
}

export function exigirAdmin(u: Usuario): void {
  if (u.rol !== "admin") throw prohibido("Solo un administrador puede hacer esto.");
}
