// Documentos que pide cada modalidad y qué le falta al usuario
// (port de co.secopradar.modelo.Preparacion).
import { CATALOGO } from "./catalogo.generado.ts";

export const TIPOS_DOCUMENTO = CATALOGO.tiposDocumento;
export type TipoDocumento = (typeof CATALOGO.tiposDocumento)[number]["id"];

const BASICOS: string[] = ["CEDULA", "INHABILIDADES", "SEGURIDAD_SOCIAL"];
export const REGISTRO_SECOP = "Cuenta de proveedor en SECOP II";

export function tipoDocumento(id: string) {
  return CATALOGO.tiposDocumento.find((t) => t.id === id) ?? null;
}

const nombreTipo = (id: string) => tipoDocumento(id)?.nombre ?? id;

/** Grupo de la guía para una modalidad de SECOP, o null si no está en la guía. */
export function grupoDeModalidad(modalidad: string | null): string | null {
  if (modalidad == null) return null;
  const m = modalidad.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase();
  if (m.includes("minima cuantia")) return "MINIMA_CUANTIA";
  if (m.includes("seleccion abreviada")) return "SELECCION_ABREVIADA";
  if (m.includes("licitacion")) return "LICITACION";
  if (m.includes("concurso de meritos")) return "CONCURSO_MERITOS";
  if (m.includes("directa") || m.includes("prestacion de servicios")) return "CONTRATACION_DIRECTA";
  return null;
}

export function requeridos(modalidad: string | null): string[] {
  const g = grupoDeModalidad(modalidad);
  const grupo = g == null ? null : CATALOGO.gruposModalidad.find((x) => x.id === g);
  return grupo ? [...grupo.requeridos] : [...BASICOS];
}

export interface DocumentoVigencia {
  tipo: string;
  /** YYYY-MM-DD o null. */
  vence: string | null;
}

/** Tipos con al menos un archivo no vencido (vence hoy todavía cuenta). */
export function vigentes(docs: DocumentoVigencia[], hoy: string): Set<string> {
  return new Set(docs.filter((d) => d.vence == null || d.vence >= hoy).map((d) => d.tipo));
}

/** Documentos que faltan para la modalidad, por nombre. */
export function documentosFaltantes(modalidad: string | null, tiposVigentes: Set<string>): string[] {
  return requeridos(modalidad).filter((t) => !tiposVigentes.has(t)).map(nombreTipo);
}

/** Hoy en Colombia como YYYY-MM-DD. */
export function hoyColombia(ahora: number = Date.now()): string {
  return new Date(ahora - 5 * 3_600_000).toISOString().substring(0, 10);
}
