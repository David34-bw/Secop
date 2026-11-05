// Inicio de sesión, registro y "Mi cuenta". Los hashes jBCrypt ($2a$) de la
// app de escritorio se verifican aquí con bcryptjs, que es compatible.
import bcrypt from "bcryptjs";
import { sql } from "../lib/bd.ts";
import { type Cuerpo, booleano, demasiados, malaPeticion, texto } from "../lib/http.ts";
import { aUsuario, cerrarSesion, COLUMNAS_USUARIO, conPlan, crearSesion, type Usuario } from "../lib/sesion.ts";
import { evaluarPassword, formatearNombre, limpiarCorreo, validarFormatoCorreo, validarNombre } from "../../_shared/dominio/validadores.ts";
import { CATALOGO } from "../../_shared/dominio/catalogo.generado.ts";

const VERSION_TERMINOS = "1.0";
const VERSION_PRIVACIDAD = "1.0";
const INTENTOS_POR_CORREO = 10;
const INTENTOS_POR_IP = 30;
const VENTANA_MINUTOS = 15;
/** Hash real de una clave cualquiera, para comparar cuando el correo no existe. */
const HASH_FALSO = bcrypt.hashSync(crypto.randomUUID(), 10);

function ipDe(req: Request): string {
  return (req.headers.get("x-forwarded-for") ?? "").split(",")[0].trim().substring(0, 64) || "desconocida";
}

/** Límite de intentos fallidos (antes en memoria del cliente; ahora en la base). */
async function revisarIntentos(email: string, ip: string): Promise<void> {
  const [r] = await sql`
    SELECT count(*) FILTER (WHERE email = ${email}) AS por_correo, count(*) FILTER (WHERE ip = ${ip}) AS por_ip
    FROM secop.intentos_login
    WHERE NOT exito AND creado > now() - make_interval(mins => ${VENTANA_MINUTOS})`;
  if (r.por_correo >= INTENTOS_POR_CORREO || r.por_ip >= INTENTOS_POR_IP) {
    throw demasiados("Demasiados intentos. Espera " + VENTANA_MINUTOS + " minutos y vuelve a intentar.");
  }
}

export async function login(c: Cuerpo, req: Request) {
  const email = limpiarCorreo(texto(c, "email", 254) ?? "");
  const password = texto(c, "password", 200) ?? "";
  const ip = ipDe(req);
  await revisarIntentos(email, ip);
  const filas = await sql.unsafe(`SELECT ${COLUMNAS_USUARIO}, password_hash FROM secop.users WHERE email = $1`, [email]);
  const hash: string | null = filas[0]?.password_hash ?? null;
  // bcrypt se calcula aunque el correo no exista, para no delatar qué correos están registrados.
  const ok = await bcrypt.compare(password, hash ?? HASH_FALSO);
  await sql`INSERT INTO secop.intentos_login (email, ip, exito) VALUES (${email}, ${ip}, ${ok && hash != null})`;
  if (!ok || hash == null) throw malaPeticion("Correo o contraseña incorrectos.");
  const usuario = aUsuario(filas[0]);
  const sesion = await crearSesion(sql, usuario.id);
  return { ...sesion, usuario: conPlan(usuario) };
}

export async function registrar(c: Cuerpo) {
  const email = limpiarCorreo(texto(c, "email", 254) ?? "");
  const password = texto(c, "password", 200) ?? "";
  const nombre = texto(c, "nombre", 200) ?? "";
  if (email === "" || password === "") throw malaPeticion("Correo y contraseña son obligatorios");
  const errorNombre = validarNombre(nombre);
  if (errorNombre) throw malaPeticion(errorNombre, { nombre: errorNombre });
  const errorCorreo = validarFormatoCorreo(email);
  if (errorCorreo) throw malaPeticion(errorCorreo, { email: errorCorreo });
  const clave = evaluarPassword(password, email, nombre);
  if (!clave.valida) {
    const m = "La contraseña no es segura: " + clave.primerError!.toLowerCase();
    throw malaPeticion(m, { password: m });
  }
  if (c.aceptaTerminos !== true) {
    throw malaPeticion("Debes aceptar los Términos y condiciones y la Política de tratamiento de datos personales");
  }
  const hash = await bcrypt.hash(password, 10);
  const pruebaHasta = new Date(Date.now() + CATALOGO.diasPrueba * 86_400_000);
  // Todo en una transacción: el bloqueo evita que dos registros a la vez creen dos administradores.
  return await sql.begin(async (tx) => {
    await tx`LOCK TABLE secop.users IN SHARE ROW EXCLUSIVE MODE`;
    const [{ existe }] = await tx`SELECT EXISTS (SELECT 1 FROM secop.users WHERE email = ${email}) AS existe`;
    if (existe) throw malaPeticion("Ya existe una cuenta con ese correo", { email: "Ya existe una cuenta con ese correo" });
    const [{ n }] = await tx`SELECT count(*) AS n FROM secop.users`;
    const rol = n === 0 ? "admin" : "usuario";
    const ahora = new Date();
    const filas = await tx.unsafe(
      `INSERT INTO secop.users (email, password_hash, nombre, rol, prueba_hasta, email_verificado,
         terminos_aceptados_at, terminos_version, privacidad_aceptada_at, privacidad_version)
       VALUES ($1, $2, $3, $4, $5, TRUE, $6, $7, $6, $8) RETURNING ${COLUMNAS_USUARIO}`,
      [email, hash, formatearNombre(nombre), rol, pruebaHasta, ahora, VERSION_TERMINOS, VERSION_PRIVACIDAD],
    );
    const usuario = aUsuario(filas[0]);
    const sesion = await crearSesion(tx, usuario.id);
    return { ...sesion, usuario: conPlan(usuario) };
  });
}

export async function logout(token: string) {
  await cerrarSesion(token);
  return { ok: true };
}

export function yo(u: Usuario) {
  return conPlan(u);
}

async function releer(id: number) {
  const filas = await sql.unsafe(`SELECT ${COLUMNAS_USUARIO} FROM secop.users WHERE id = $1`, [id]);
  return conPlan(aUsuario(filas[0]));
}

export async function cambiarNombre(u: Usuario, c: Cuerpo) {
  const nombre = texto(c, "nombre", 200) ?? "";
  const e = validarNombre(nombre);
  if (e) throw malaPeticion(e, { nombre: e });
  await sql`UPDATE secop.users SET nombre = ${formatearNombre(nombre)} WHERE id = ${u.id}`;
  return await releer(u.id);
}

export async function cambiarPassword(u: Usuario, c: Cuerpo) {
  const actual = texto(c, "actual", 200) ?? "";
  const nueva = texto(c, "nueva", 200) ?? "";
  const [{ password_hash }] = await sql`SELECT password_hash FROM secop.users WHERE id = ${u.id}`;
  if (!password_hash || !(await bcrypt.compare(actual, password_hash))) {
    throw malaPeticion("La contraseña actual no es correcta", { actual: "La contraseña actual no es correcta" });
  }
  const clave = evaluarPassword(nueva, u.email, u.nombre);
  if (!clave.valida) throw malaPeticion("La contraseña nueva no es segura: " + clave.primerError!.toLowerCase());
  if (nueva === actual) throw malaPeticion("La contraseña nueva debe ser distinta de la actual");
  await sql.begin(async (tx) => {
    await tx`UPDATE secop.users SET password_hash = ${await bcrypt.hash(nueva, 10)} WHERE id = ${u.id}`;
    // Cambiar la contraseña cierra las demás sesiones abiertas.
    await tx`DELETE FROM secop.sesiones WHERE usuario_id = ${u.id}`;
  });
  return { ...(await crearSesion(sql, u.id)), usuario: await releer(u.id) };
}

export async function avisos(u: Usuario, c: Cuerpo) {
  await sql`UPDATE secop.users SET alertas_activas = ${booleano(c, "activos")} WHERE id = ${u.id}`;
  return await releer(u.id);
}
