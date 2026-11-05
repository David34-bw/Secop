// SECOP RADAR — API por HTTPS (puerto 443) para redes donde el firewall
// bloquea PostgreSQL. Una sola función con enrutador: cada ruta es una
// operación de dominio (POST /functions/v1/api/<operacion>). No hay ninguna
// ruta que reciba SQL ni nombres de tabla.
//
// Cabeceras:
//   apikey: clave publishable del proyecto (la única clave que tiene la app)
//   x-secop-sesion: token de sesión (lo da auth.login / auth.registrar)
//   x-secop-job: secreto del job programado (solo pg_cron y el propio job)
import { type Cuerpo, error, ErrorApi, json, malaPeticion, noEncontrado, prohibido } from "./lib/http.ts";
import { exigirAdmin, type Usuario, usuarioDeSesion } from "./lib/sesion.ts";
import * as cuenta from "./ops/cuenta.ts";
import * as procesos from "./ops/procesos.ts";
import * as perfiles from "./ops/perfiles.ts";
import * as documentos from "./ops/documentos.ts";
import * as postulaciones from "./ops/postulaciones.ts";
import * as admin from "./ops/admin.ts";
import * as job from "./ops/job.ts";

type Publica = (c: Cuerpo, req: Request) => Promise<unknown> | unknown;
type ConSesion = (u: Usuario, c: Cuerpo, token: string) => Promise<unknown> | unknown;

/** Sin sesión: solo la clave publishable. */
const PUBLICAS: Record<string, Publica> = {
  "auth.login": (c, req) => cuenta.login(c, req),
  "auth.registrar": (c) => cuenta.registrar(c),
  "estado.datos": () => procesos.estadoDatos(),
};

/** Con sesión: el usuario sale del token. */
const DE_USUARIO: Record<string, ConSesion> = {
  "auth.logout": (_u, _c, token) => cuenta.logout(token),
  "cuenta.yo": (u) => cuenta.yo(u),
  "cuenta.cambiarNombre": (u, c) => cuenta.cambiarNombre(u, c),
  "cuenta.cambiarPassword": (u, c) => cuenta.cambiarPassword(u, c),
  "cuenta.avisos": (u, c) => cuenta.avisos(u, c),
  "inicio.datos": () => procesos.inicioDatos(),
  "procesos.vigentes": (_u, c) => procesos.vigentes(c),
  "procesos.porIds": (_u, c) => procesos.porIds(c),
  "oportunidades.vigentes": (u, c) => procesos.oportunidades(u, c),
  "oportunidades.marcarVistas": (u, c) => procesos.marcarVistas(u, c),
  "oportunidades.ocultar": (u, c) => procesos.ocultar(u, c, true),
  "oportunidades.mostrar": (u, c) => procesos.ocultar(u, c, false),
  "oportunidades.ocultos": (u) => procesos.ocultos(u),
  "perfiles.listar": (u) => perfiles.listar(u),
  "perfiles.crear": (u, c) => perfiles.crear(u, c),
  "perfiles.eliminar": (u, c) => perfiles.eliminar(u, c),
  "proponente.obtener": (u) => documentos.obtenerProponente(u),
  "proponente.guardar": (u, c) => documentos.guardarDatos(u, c),
  "documentos.listar": (u) => documentos.listar(u),
  "documentos.pedirSubida": (u, c) => documentos.pedirSubida(u, c, "documento", null),
  "documentos.confirmarSubida": (u, c) => documentos.confirmarSubida(u, c),
  "documentos.descargar": (u, c) => documentos.descargar(u, c),
  "documentos.eliminar": (u, c) => documentos.eliminar(u, c),
  "documentos.cambiarVence": (u, c) => documentos.cambiarVence(u, c),
  "postulaciones.listar": (u) => postulaciones.listar(u),
  "postulaciones.idsPorProceso": (u) => postulaciones.idsPorProceso(u),
  "postulaciones.crear": (u, c) => postulaciones.crear(u, c),
  "postulaciones.obtener": (u, c) => postulaciones.obtener(u, c),
  "postulaciones.actualizar": (u, c) => postulaciones.actualizar(u, c),
  "postulaciones.eliminar": (u, c) => postulaciones.eliminar(u, c),
  "postulaciones.pedirSubidaPropuesta": (u, c) => postulaciones.pedirSubidaPropuesta(u, c),
  "postulaciones.confirmarSubidaPropuesta": (u, c) => postulaciones.confirmarSubidaPropuesta(u, c),
  "postulaciones.descargarPropuesta": (u, c) => postulaciones.descargarPropuesta(u, c),
};

/** Solo administradores (rol revisado en el servidor). */
const DE_ADMIN: Record<string, ConSesion> = {
  "admin.resumen": () => admin.resumen(),
  "admin.usuarios": () => admin.usuarios(),
  "admin.historial": (_u, c) => admin.historial(c),
  "admin.asignarPlan": (_u, c) => admin.asignarPlan(c),
  "admin.asignarRol": (_u, c) => admin.asignarRol(c),
  "admin.reiniciarPrueba": (_u, c) => admin.reiniciarPrueba(c),
  "admin.actualizarAhora": (_u, c) => admin.actualizarAhora(c),
};

/** Solo el job programado (secreto SECOP_JOB_SECRET). */
const DEL_JOB: Record<string, (c: Cuerpo) => Promise<unknown>> = {
  "job.ingesta": (c) => job.ingesta(c),
  "job.cruce": (c) => job.cruce(c),
  "job.planes": () => job.planes(),
};

/** La app debe mandar la clave publishable del proyecto (o la anon heredada). */
function exigirClave(req: Request): void {
  const recibida = req.headers.get("apikey") ?? "";
  const validas = new Set<string>(Object.values(JSON.parse(Deno.env.get("SUPABASE_PUBLISHABLE_KEYS") ?? "{}")));
  const anon = Deno.env.get("SUPABASE_ANON_KEY");
  if (anon) validas.add(anon);
  if (recibida === "" || !validas.has(recibida)) throw prohibido("Falta la clave de la aplicación.");
}

const MAX_CUERPO = 256 * 1024;

async function leerCuerpo(req: Request): Promise<Cuerpo> {
  const largo = Number(req.headers.get("content-length") ?? "0");
  if (largo > MAX_CUERPO) throw malaPeticion("La petición es demasiado grande.");
  const textoCuerpo = await req.text();
  if (textoCuerpo.length > MAX_CUERPO) throw malaPeticion("La petición es demasiado grande.");
  if (textoCuerpo.trim() === "") return {};
  let c: unknown;
  try {
    c = JSON.parse(textoCuerpo);
  } catch {
    throw malaPeticion("El cuerpo debe ser JSON.");
  }
  if (c == null || typeof c !== "object" || Array.isArray(c)) throw malaPeticion("El cuerpo debe ser un objeto JSON.");
  return c as Cuerpo;
}

Deno.serve(async (req) => {
  try {
    if (req.method !== "POST") throw new ErrorApi(405, "metodo", "Usa POST.");
    const operacion = new URL(req.url).pathname.split("/").filter(Boolean).pop() ?? "";
    const cuerpo = await leerCuerpo(req);

    if (DEL_JOB[operacion]) {
      job.exigirSecreto(req);
      return json(await DEL_JOB[operacion](cuerpo));
    }
    exigirClave(req);
    if (PUBLICAS[operacion]) return json(await PUBLICAS[operacion](cuerpo, req));

    const manejador = DE_USUARIO[operacion] ?? DE_ADMIN[operacion];
    if (!manejador) throw noEncontrado("Operación desconocida: " + operacion.substring(0, 60));
    const { usuario, token } = await usuarioDeSesion(req);
    if (DE_ADMIN[operacion]) exigirAdmin(usuario);
    return json(await manejador(usuario, cuerpo, token));
  } catch (e) {
    return error(e);
  }
});
