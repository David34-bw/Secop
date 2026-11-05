// Validación de nombre, contraseña y correo (port de ValidadorNombre,
// ValidadorPassword y CuentaService.validarFormatoCorreo). El servidor vuelve
// a validar todo lo que la app ya validó: nunca se confía en el cliente.
import { normalizar, palabras } from "./texto.ts";

// ---------- utilidades con la semántica de Java ----------

/** String.trim() de Java: quita caracteres <= U+0020 en los bordes. */
function trimJava(s: string): string {
  let i = 0;
  let j = s.length;
  while (i < j && s.charCodeAt(i) <= 0x20) i++;
  while (j > i && s.charCodeAt(j - 1) <= 0x20) j--;
  return s.substring(i, j);
}

/** \s de Java (solo ASCII). */
const ESPACIOS_JAVA = /[ \t\n\x0B\f\r]+/g;

/** Character.isWhitespace de Java (sin los espacios que no separan, como U+00A0). */
function esEspacioJava(c: string): boolean {
  const n = c.charCodeAt(0);
  if ((n >= 0x09 && n <= 0x0d) || (n >= 0x1c && n <= 0x1f)) return true;
  if (n === 0xa0 || n === 0x2007 || n === 0x202f) return false;
  return /[\p{Zs}\p{Zl}\p{Zp}]/u.test(c);
}

const esLetra = (c: string) => /\p{L}/u.test(c);
const esDigito = (c: string) => /\p{Nd}/u.test(c);

// ---------- Nombre ----------

const NOMBRE_MIN = 2;
const NOMBRE_MAX = 60;
const MAX_PALABRAS = 5;
const PARTICULAS = new Set(["de", "del", "la", "las", "los", "y", "e", "da", "do", "dos", "van", "von", "di", "le"]);
const RELLENO = new Set([
  "prueba", "pruebas", "test", "tester", "usuario", "user", "admin", "administrador", "nombre", "apellido",
  "ninguno", "ninguna", "nadie", "anonimo", "anonima", "null", "none", "nada", "xxx", "abc", "hola",
  "asdf", "qwerty", "demo", "ejemplo", "fulano", "mengano", "perengano", "nn",
]);
const TECLAZOS = ["qwer", "wert", "erty", "asdf", "sdfg", "dfgh", "fghj", "ghjk", "hjkl",
  "zxcv", "xcvb", "cvbn", "vbnm", "poiu", "lkjh", "mnbv", "abcd", "bcde"];

export function limpiarNombre(nombre: string | null | undefined): string {
  return nombre == null ? "" : trimJava(nombre).replace(ESPACIOS_JAVA, " ");
}

/** (\p{L})\1\1 sin distinguir mayúsculas solo en ASCII, como Java con CASE_INSENSITIVE. */
function tresLetrasIguales(palabra: string): boolean {
  const cps = Array.from(palabra);
  const igual = (a: string, b: string) => a === b || (/^[A-Za-z]$/.test(a) && a.toLowerCase() === b.toLowerCase());
  for (let i = 2; i < cps.length; i++) {
    if (esLetra(cps[i - 2]) && igual(cps[i - 2], cps[i - 1]) && igual(cps[i - 2], cps[i])) return true;
  }
  return false;
}

/** Mensaje de error, o null si el nombre es válido. */
export function validarNombre(crudo: string | null | undefined): string | null {
  const nombre = limpiarNombre(crudo);
  if (nombre === "") return "Escribe tu nombre";
  if (nombre.length < NOMBRE_MIN) return "El nombre es muy corto";
  if (nombre.length > NOMBRE_MAX) return "El nombre es muy largo (máximo " + NOMBRE_MAX + " caracteres)";
  if (/\d/.test(nombre)) return "El nombre no puede tener números";
  if (!/^[\p{L}' -]+$/u.test(nombre)) {
    return "Usa solo letras, espacios, guion o apóstrofo (sin símbolos como @, ., _ o emojis)";
  }
  const ps = nombre.split(" ");
  if (ps.length > MAX_PALABRAS) return "Escribe tu nombre y apellidos (máximo " + MAX_PALABRAS + " palabras)";

  let hayPalabraReal = false;
  for (const palabra of ps) {
    const p = normalizar(palabra).replaceAll(" ", "");
    if (p === "") return "Cada parte del nombre debe tener letras";
    if (palabra.startsWith("-") || palabra.startsWith("'") || palabra.endsWith("-") || palabra.endsWith("'")) {
      return "El guion o el apóstrofo deben ir entre letras (ej: María-José, D'Alessandro)";
    }
    if (PARTICULAS.has(p)) continue;
    if (p.length < 2) return "Escribe el nombre completo, sin iniciales sueltas";
    if (RELLENO.has(p)) return "Escribe tu nombre real";
    if (!/[aeiouy]/.test(p)) return "«" + palabra + "» no parece un nombre (no tiene vocales)";
    if (tresLetrasIguales(palabra)) return "«" + palabra + "» tiene letras repetidas de más";
    if (/[bcdfghjklmnpqrstvwxz]{5}/.test(p)) return "«" + palabra + "» no parece un nombre";
    for (const t of TECLAZOS) if (p.includes(t)) return "«" + palabra + "» no parece un nombre";
    hayPalabraReal = true;
  }
  if (!hayPalabraReal) return "Escribe tu nombre";
  if (RELLENO.has(normalizar(nombre))) return "Escribe tu nombre real";
  return null;
}

function capitalizarPartes(palabra: string): string {
  let sb = "";
  let siguienteMayuscula = true;
  for (const c of palabra) {
    const m = c.toUpperCase();
    sb += siguienteMayuscula ? (m.length === c.length ? m : c) : c;
    siguienteMayuscula = c === "-" || c === "'";
  }
  return sb;
}

/** "MARÍA DE LA cruz" -> "María de la Cruz". */
export function formatearNombre(crudo: string | null | undefined): string {
  const ps = limpiarNombre(crudo).toLocaleLowerCase("es-CO").split(" ");
  return ps.map((p, i) => (i > 0 && PARTICULAS.has(normalizar(p)) ? p : capitalizarPartes(p))).join(" ");
}

// ---------- Contraseña ----------

export const PASSWORD_MIN = 10;
export const PASSWORD_MAX_BYTES = 72;
export type Nivel = "MUY_DEBIL" | "DEBIL" | "MEDIA" | "FUERTE";
export interface Regla {
  descripcion: string;
  cumple: boolean;
}
export interface ResultadoPassword {
  reglas: Regla[];
  nivel: Nivel;
  valida: boolean;
  primerError: string | null;
}

const COMUNES = new Set([
  "password", "contrasena", "clave", "qwerty", "qwertyuiop", "asdfgh", "asdfghjkl", "zxcvbn", "iloveyou",
  "teamo", "admin", "administrador", "welcome", "bienvenido", "letmein", "secop", "secopradar", "radar",
  "colombia", "bogota", "medellin", "football", "futbol", "monkey", "dragon", "princess", "sunshine",
  "abc", "abcdef", "usuario", "user", "login", "master", "hola", "holamundo",
]);
const FILAS_TECLADO = ["qwertyuiop", "asdfghjklñ", "zxcvbnm", "1234567890"];

/** Recorre unidades UTF-16, como String.chars() en Java. */
function algunChar(p: string, f: (c: string) => boolean): boolean {
  for (let i = 0; i < p.length; i++) if (f(p[i])) return true;
  return false;
}

function tresIguales(p: string): boolean {
  for (let i = 2; i < p.length; i++) if (p[i] === p[i - 1] && p[i] === p[i - 2]) return true;
  return false;
}

function tieneSecuencia(p: string): boolean {
  for (let i = 3; i < p.length; i++) {
    const d1 = p.charCodeAt(i - 2) - p.charCodeAt(i - 3);
    const d2 = p.charCodeAt(i - 1) - p.charCodeAt(i - 2);
    const d3 = p.charCodeAt(i) - p.charCodeAt(i - 1);
    if ((d1 === 1 || d1 === -1) && d1 === d2 && d2 === d3 && (esLetra(p[i]) || esDigito(p[i]))) return true;
  }
  for (const fila of FILAS_TECLADO) {
    for (let i = 0; i + 4 <= fila.length; i++) {
      const trozo = fila.substring(i, i + 4);
      if (p.includes(trozo) || p.includes([...trozo].reverse().join(""))) return true;
    }
  }
  return false;
}

function esComun(p: string): boolean {
  const base = normalizar(p).replace(/[^a-z]/g, "");
  if (base === "") return true;
  if (COMUNES.has(base)) return true;
  for (const c of COMUNES) if (c.length >= 6 && base.includes(c) && base.length - c.length <= 3) return true;
  return false;
}

function contieneDatosPersonales(p: string, email: string | null, nombre: string | null): boolean {
  const clave = normalizar(p).replaceAll(" ", "");
  const partes: string[] = [];
  if (email != null && email.includes("@")) partes.push(...palabras(email.substring(0, email.indexOf("@"))));
  if (nombre != null) partes.push(...palabras(nombre));
  return partes.some((parte) => parte.length >= 4 && clave.includes(parte));
}

export function evaluarPassword(password: string | null | undefined, email: string | null, nombre: string | null): ResultadoPassword {
  const p = password ?? "";
  const reglas: Regla[] = [
    { descripcion: "Al menos " + PASSWORD_MIN + " caracteres", cumple: p.length >= PASSWORD_MIN },
    { descripcion: "Una letra mayúscula", cumple: algunChar(p, (c) => /\p{Lu}/u.test(c)) },
    { descripcion: "Una letra minúscula", cumple: algunChar(p, (c) => /\p{Ll}/u.test(c)) },
    { descripcion: "Un número", cumple: algunChar(p, esDigito) },
    {
      descripcion: "Un símbolo (por ejemplo ! # $ % & * ? @)",
      cumple: algunChar(p, (c) => !esLetra(c) && !esDigito(c) && !esEspacioJava(c)),
    },
    { descripcion: "Sin espacios", cumple: p !== "" && !algunChar(p, esEspacioJava) },
    { descripcion: "Sin 3 caracteres iguales seguidos (aaa, 111)", cumple: p !== "" && !tresIguales(p) },
    { descripcion: "Sin secuencias como 1234, abcd o qwer", cumple: p !== "" && !tieneSecuencia(p.toLowerCase()) },
    { descripcion: "Que no sea una contraseña común", cumple: p !== "" && !esComun(p) },
    { descripcion: "Que no contenga tu correo ni tu nombre", cumple: p !== "" && !contieneDatosPersonales(p, email, nombre) },
  ];
  if (new TextEncoder().encode(p).length > PASSWORD_MAX_BYTES) {
    reglas.push({ descripcion: "Máximo " + PASSWORD_MAX_BYTES + " caracteres", cumple: false });
  }
  const cumplidas = reglas.filter((r) => r.cumple).length;
  const nivel: Nivel = cumplidas === reglas.length && p.length >= 14 ? "FUERTE"
    : cumplidas === reglas.length ? "MEDIA"
    : cumplidas >= reglas.length - 3 ? "DEBIL"
    : "MUY_DEBIL";
  const falta = reglas.find((r) => !r.cumple);
  return { reglas, nivel, valida: falta == null, primerError: falta?.descripcion ?? null };
}

// ---------- Correo (solo formato; el DNS lo revisa la app) ----------

const FORMATO_CORREO =
  /^(?=.{1,64}@)[a-z0-9_%+-]+(\.[a-z0-9_%+-]+)*@([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,24}$/;
const DOMINIOS_TEMPORALES = new Set([
  "mailinator.com", "yopmail.com", "yopmail.net", "10minutemail.com", "10minutemail.net", "guerrillamail.com",
  "guerrillamail.info", "temp-mail.org", "temp-mail.io", "tempmail.com", "tempmail.net", "trashmail.com",
  "getnada.com", "dispostable.com", "sharklasers.com", "maildrop.cc", "mailnesia.com", "throwawaymail.com",
  "fakeinbox.com", "emailondeck.com", "mintemail.com", "mohmal.com", "burnermail.io", "tempr.email",
  "discard.email", "spamgourmet.com", "mailcatch.com", "tempinbox.com", "moakt.com", "emailfake.com",
]);
const DOMINIOS_DE_EJEMPLO = new Set([
  "example.com", "example.org", "example.net", "test.com", "prueba.com", "correo.com", "email.com",
  "dominio.com", "ejemplo.com", "asdf.com", "abc.com", "xxx.com", "aaa.com",
]);
const EXTENSIONES_RESERVADAS = new Set(["test", "example", "invalid", "localhost", "local"]);
const DOMINIOS_MAL_ESCRITOS: Record<string, string> = {
  "gmial.com": "gmail.com", "gmal.com": "gmail.com", "gmail.co": "gmail.com", "gamil.com": "gmail.com",
  "gmail.con": "gmail.com", "gmail.cm": "gmail.com", "gmai.com": "gmail.com", "gmil.com": "gmail.com",
  "gnail.com": "gmail.com", "gmail.om": "gmail.com", "hotmial.com": "hotmail.com", "hotmail.co": "hotmail.com",
  "hotmail.con": "hotmail.com", "hotmal.com": "hotmail.com", "hotmil.com": "hotmail.com",
  "outlok.com": "outlook.com", "outlook.con": "outlook.com", "outloo.com": "outlook.com",
  "yaho.com": "yahoo.com", "yahoo.con": "yahoo.com", "yahho.com": "yahoo.com", "live.con": "live.com",
  "icloud.con": "icloud.com",
};

export function limpiarCorreo(email: string | null | undefined): string {
  return email == null ? "" : trimJava(email).toLowerCase();
}

export function validarFormatoCorreo(limpio: string): string | null {
  if (limpio === "") return "Escribe tu correo";
  if (limpio.length > 254 || limpio.split("@").length - 1 !== 1 || !FORMATO_CORREO.test(limpio)) {
    return "Escribe un correo válido, por ejemplo nombre@gmail.com";
  }
  const [usuario, dominio] = limpio.split("@");
  const sugerido = DOMINIOS_MAL_ESCRITOS[dominio];
  if (sugerido) return "¿Quisiste decir " + usuario + "@" + sugerido + "?";
  if (DOMINIOS_TEMPORALES.has(dominio)) return "No se permiten correos temporales";
  const extension = dominio.substring(dominio.lastIndexOf(".") + 1);
  if (DOMINIOS_DE_EJEMPLO.has(dominio) || EXTENSIONES_RESERVADAS.has(extension)) return "Usa tu correo real, no uno de ejemplo";
  return null;
}
