// Descarga de SECOP II (datos.gov.co, conjunto p6dx-8zbt): consulta y
// normalización de cada fila (port de IngestaService). Sin red ni base: el
// job usa estas funciones y hace el fetch y el SQL.
import { CATALOGO } from "./catalogo.generado.ts";
import { recortar } from "./texto.ts";

export const BASE_SOCRATA = "https://www.datos.gov.co/resource/p6dx-8zbt.json";
export const PAGINA = 1000;

export interface FilaProceso {
  id: string | null;
  entidad: string | null;
  nombre: string | null;
  objeto: string | null;
  modalidad: string | null;
  categoria: string | null;
  tipoContrato: string | null;
  valor: number | null;
  departamento: string | null;
  ciudad: string | null;
  estadoApertura: string | null;
  fechaPublicacion: string | null;
  fechaCierre: string | null;
  url: string | null;
  ordenEntidad: string | null;
  estadoProcedimiento: string | null;
  respuestas: number | null;
  raw: string;
}

/** URL de una página: publicados desde `desde` (YYYY-MM-DD) en las modalidades competitivas. */
export function urlPagina(desde: string, offset: number): string {
  const lista = CATALOGO.modalidadesCompetitivas.map((m) => "'" + m + "'").join(",");
  const where = "fecha_de_publicacion_del > '" + desde + "T00:00:00.000' AND modalidad_de_contratacion in(" + lista + ")";
  const params = new URLSearchParams();
  params.set("$where", where);
  params.set("$order", "fecha_de_publicacion_del DESC");
  params.set("$limit", String(PAGINA));
  params.set("$offset", String(offset));
  return BASE_SOCRATA + "?" + params.toString();
}

/** Día de hoy en Colombia menos `dias`, como YYYY-MM-DD. */
export function desdeHace(dias: number, ahora: number = Date.now()): string {
  const bogota = new Date(ahora - 5 * 3_600_000);
  bogota.setUTCDate(bogota.getUTCDate() - dias);
  return bogota.toISOString().substring(0, 10);
}

function texto(p: Record<string, unknown>, campo: string): string | null {
  const v = p[campo];
  if (v == null) return null;
  const s = typeof v === "string" ? v : typeof v === "object" ? JSON.stringify(v) : String(v);
  return s === "" ? null : s;
}

function numero(p: Record<string, unknown>, campo: string): number | null {
  const s = texto(p, campo);
  if (s == null || !/^[\s]*[-+]?(\d+\.?\d*|\.\d+)([eE][-+]?\d+)?[\s]*$/.test(s)) return null;
  return Math.round(Number(s));
}

/**
 * SECOP entrega fechas sin zona horaria ("2026-10-19T00:00:00.000"): son
 * días de Colombia (UTC-5, sin horario de verano). El cierre a medianoche
 * se toma como el final de ese día. Devuelve ISO en UTC o null.
 */
export function fechaColombia(s: string | null, esCierre: boolean): string | null {
  if (s == null) return null;
  const t = s.length > 23 ? s.substring(0, 23) : s;
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?$/.exec(t);
  if (!m) return null;
  const [y, mes, d, h, mi] = [m[1], m[2], m[3], m[4], m[5]].map(Number);
  let seg = m[6] ? Number(m[6]) : 0;
  const frac = m[7] ? m[7] : "";
  let ms = frac ? Number((frac + "00").substring(0, 3)) : 0;
  const nanosCero = frac === "" || /^0+$/.test(frac);
  if (mes < 1 || mes > 12 || h > 23 || mi > 59 || seg > 59) return null;
  const diasMes = new Date(Date.UTC(y, mes, 0)).getUTCDate();
  if (d < 1 || d > diasMes) return null;
  let hora = h, minuto = mi;
  if (esCierre && h === 0 && mi === 0 && seg === 0 && nanosCero) {
    hora = 23;
    minuto = 59;
    seg = 59;
    ms = 0;
  }
  return new Date(Date.UTC(y, mes - 1, d, hora + 5, minuto, seg, ms)).toISOString();
}

export function normalizarFila(p: Record<string, unknown>): FilaProceso {
  const objeto = texto(p, "descripci_n_del_procedimiento") ?? texto(p, "nombre_del_procedimiento");
  const url = p["urlproceso"] && typeof (p["urlproceso"] as Record<string, unknown>)["url"] === "string"
    ? (p["urlproceso"] as Record<string, string>)["url"]
    : null;
  return {
    id: texto(p, "id_del_proceso"),
    entidad: recortar(texto(p, "entidad"), 1000),
    nombre: recortar(texto(p, "nombre_del_procedimiento"), 4000),
    objeto: recortar(objeto, 20000),
    modalidad: texto(p, "modalidad_de_contratacion"),
    categoria: texto(p, "codigo_principal_de_categoria"),
    tipoContrato: texto(p, "tipo_de_contrato"),
    valor: numero(p, "precio_base"),
    departamento: texto(p, "departamento_entidad"),
    ciudad: texto(p, "ciudad_entidad"),
    estadoApertura: texto(p, "estado_de_apertura_del_proceso"),
    fechaPublicacion: fechaColombia(texto(p, "fecha_de_publicacion_del"), false),
    fechaCierre: fechaColombia(texto(p, "fecha_de_recepcion_de"), true),
    url,
    ordenEntidad: texto(p, "ordenentidad"),
    estadoProcedimiento: texto(p, "estado_del_procedimiento"),
    respuestas: numero(p, "respuestas_al_procedimiento"),
    raw: JSON.stringify(p),
  };
}
