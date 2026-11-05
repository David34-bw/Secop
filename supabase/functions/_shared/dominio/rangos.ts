// Rangos de valor de los procesos (port de co.secopradar.modelo.RangoValor).
import { CATALOGO } from "./catalogo.generado.ts";

export interface RangoValor {
  id: string;
  min: number;
  /** null = sin tope. */
  max: number | null;
}

const PREFIJO_OTRO = "otro:";

const FIJOS: RangoValor[] = CATALOGO.rangosValor.map((r) => ({ id: r.id, min: r.min, max: r.max < 0 ? null : r.max }));

/** Acepta los rangos fijos y los escritos por la persona ("otro:MIN-MAX", MAX vacío = sin tope). */
export function rangoPorId(id: string | null | undefined): RangoValor | null {
  if (id == null) return null;
  if (id.startsWith(PREFIJO_OTRO)) {
    const partes = id.substring(PREFIJO_OTRO.length).split("-");
    if (partes.length !== 2) return null;
    if (!/^\d+$/.test(partes[0]) || (partes[1] !== "" && !/^\d+$/.test(partes[1]))) return null;
    return personalizado(Number(partes[0]), partes[1] === "" ? null : Number(partes[1]));
  }
  return FIJOS.find((r) => r.id === id) ?? null;
}

/** Rango propio: mínimo >= 0 y máximo (opcional) mayor que el mínimo. */
export function personalizado(min: number, max: number | null): RangoValor | null {
  if (min < 0 || (max != null && max <= min) || (max == null && min === 0)) return null;
  return { id: PREFIJO_OTRO + min + "-" + (max == null ? "" : max), min, max };
}

export function contiene(r: RangoValor, valor: number): boolean {
  return valor >= r.min && (r.max == null || valor < r.max);
}
