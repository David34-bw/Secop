// Etapas de una postulación y sus reglas (port de Avance y PostulacionesService).
// Las etapas van en orden: no se marca una si falta una anterior, ni se
// desmarca una si hay otra posterior marcada por el usuario.
import { documentosFaltantes } from "./preparacion.ts";

export const ETAPAS = ["REGISTRO", "REVISAR", "PLIEGO", "DOCUMENTOS", "PROPUESTA", "PRESENTAR", "EVALUACION"] as const;
export type Etapa = (typeof ETAPAS)[number];

export const TITULOS: Record<Etapa, string> = {
  REGISTRO: "Registrarte en SECOP II",
  REVISAR: "Revisar el proceso",
  PLIEGO: "Leer la invitación o el pliego",
  DOCUMENTOS: "Reunir los documentos",
  PROPUESTA: "Preparar la propuesta económica",
  PRESENTAR: "Presentar la oferta",
  EVALUACION: "Revisar la evaluación",
};

export type Resultado = "GANADA" | "NO_GANADA" | "SUBSANAR";

export interface Requisito {
  texto: string;
  cumplido: boolean;
}

export interface Postulacion {
  modalidad: string | null;
  valor: number | null;
  fechaCierre: string | null;
  revisado: boolean;
  pliegoLeido: boolean;
  requisitosExtra: Requisito[];
  valorOfertado: number | null;
  presentadaAt: string | null;
  resultado: Resultado | null;
  plazoSubsanar: string | null;
  desistida: boolean;
}

export interface Contexto {
  registradoSecop: boolean;
  tiposVigentes: Set<string>;
}

export class ErrorPostulacion extends Error {}

export function faltantesDocumentos(p: Postulacion, c: Contexto): string[] {
  const faltan = documentosFaltantes(p.modalidad, c.tiposVigentes);
  for (const r of p.requisitosExtra) if (!r.cumplido) faltan.push(r.texto);
  return faltan;
}

export function completa(e: Etapa, p: Postulacion, c: Contexto): boolean {
  switch (e) {
    case "REGISTRO": return c.registradoSecop;
    case "REVISAR": return p.revisado;
    case "PLIEGO": return p.pliegoLeido;
    case "DOCUMENTOS": return faltantesDocumentos(p, c).length === 0;
    case "PROPUESTA": return p.valorOfertado != null;
    case "PRESENTAR": return p.presentadaAt != null;
    case "EVALUACION": return p.resultado === "GANADA" || p.resultado === "NO_GANADA";
  }
}

export function actual(p: Postulacion, c: Contexto): Etapa | null {
  return ETAPAS.find((e) => !completa(e, p, c)) ?? null;
}

export function habilitada(e: Etapa, p: Postulacion, c: Contexto): boolean {
  for (const anterior of ETAPAS) {
    if (anterior === e) return true;
    if (!completa(anterior, p, c)) return false;
  }
  return true;
}

/** Resumen del avance para la respuesta de la API. */
export function avance(p: Postulacion, c: Contexto) {
  const a = actual(p, c);
  return {
    actual: a,
    completas: a == null ? ETAPAS.length : ETAPAS.indexOf(a),
    estados: Object.fromEntries(ETAPAS.map((e) => [e,
      completa(e, p, c) && habilitada(e, p, c) ? "COMPLETA" : e === a ? "ACTUAL" : "BLOQUEADA"])),
    faltantesDocumentos: faltantesDocumentos(p, c),
  };
}

const numero = (e: Etapa) => ETAPAS.indexOf(e) + 1;

/** Lanza ErrorPostulacion si la etapa no se puede trabajar todavía. */
export function exigir(e: Etapa, p: Postulacion, c: Contexto): void {
  if (p.desistida) throw new ErrorPostulacion("Desististe de este proceso. Reanúdalo para seguir.");
  if (!habilitada(e, p, c)) {
    const falta = actual(p, c)!;
    throw new ErrorPostulacion("Primero completa la etapa " + numero(falta) + ": " + TITULOS[falta] + ".");
  }
}

/** No se desmarca una etapa si ya hay otra posterior marcada por el usuario. */
export function exigirSinPosteriores(e: Etapa, p: Postulacion): void {
  const marcadas = [false, p.revisado, p.pliegoLeido, false, p.valorOfertado != null, p.presentadaAt != null, p.resultado != null];
  for (let i = ETAPAS.indexOf(e) + 1; i < marcadas.length; i++) {
    if (marcadas[i]) {
      throw new ErrorPostulacion("Primero desmarca la etapa " + (i + 1) + ": " + TITULOS[ETAPAS[i]] + ".");
    }
  }
}

export function cerrado(p: Postulacion, ahora: number = Date.now()): boolean {
  return p.fechaCierre != null && Date.parse(p.fechaCierre) < ahora;
}

/** Acciones que la app puede pedir sobre una postulación. */
export type Accion =
  | { accion: "revisar"; valor: boolean }
  | { accion: "pliego"; valor: boolean; notas?: string | null }
  | { accion: "notas"; notas: string | null }
  | { accion: "requisitoAgregar"; texto: string }
  | { accion: "requisitoAlternar"; indice: number }
  | { accion: "requisitoQuitar"; indice: number }
  | { accion: "propuesta"; valor: number | null }
  | { accion: "presentar"; valor: boolean }
  | { accion: "resultado"; resultado: Resultado | null; plazo?: string | null }
  | { accion: "desistir"; valor: boolean };

function limpiarTexto(s: string | null | undefined, maximo: number): string | null {
  if (s == null) return null;
  const t = s.trim();
  if (t === "") return null;
  return t.length > maximo ? t.substring(0, maximo) : t;
}

/**
 * Aplica una acción con todas las reglas y devuelve los campos a guardar.
 * `hoy` es YYYY-MM-DD en Colombia; `ahora` en milisegundos.
 */
export function aplicar(a: Accion, p: Postulacion, c: Contexto, hoy: string, ahora: number): Partial<Postulacion> & { notas?: string | null } {
  switch (a.accion) {
    case "revisar": {
      exigir("REVISAR", p, c);
      if (!a.valor) exigirSinPosteriores("REVISAR", p);
      if (a.valor && cerrado(p, ahora)) throw new ErrorPostulacion("Este proceso ya cerró: ya no alcanzas a presentarte.");
      return { revisado: a.valor === true };
    }
    case "pliego": {
      exigir("PLIEGO", p, c);
      if (!a.valor) exigirSinPosteriores("PLIEGO", p);
      return { pliegoLeido: a.valor === true, notas: limpiarTexto(a.notas, 4000) };
    }
    case "notas":
      return { notas: limpiarTexto(a.notas, 4000) };
    case "requisitoAgregar": {
      exigir("PLIEGO", p, c);
      const t = limpiarTexto(a.texto, 200);
      if (t == null || !/\p{L}/u.test(t)) throw new ErrorPostulacion("Escribe qué pide el pliego, ej: Certificado de curso de alturas.");
      if (p.requisitosExtra.length >= 30) throw new ErrorPostulacion("Máximo 30 requisitos por proceso.");
      if (p.requisitosExtra.some((r) => r.texto.toLowerCase() === t.toLowerCase())) throw new ErrorPostulacion("Ese requisito ya está en la lista.");
      return { requisitosExtra: [...p.requisitosExtra, { texto: t, cumplido: false }] };
    }
    case "requisitoAlternar":
    case "requisitoQuitar": {
      exigir("PLIEGO", p, c);
      if (!Number.isInteger(a.indice) || a.indice < 0 || a.indice >= p.requisitosExtra.length) {
        throw new ErrorPostulacion("Ese requisito no existe.");
      }
      const l = [...p.requisitosExtra];
      if (a.accion === "requisitoQuitar") l.splice(a.indice, 1);
      else l[a.indice] = { texto: l[a.indice].texto, cumplido: !l[a.indice].cumplido };
      return { requisitosExtra: l };
    }
    case "propuesta": {
      exigir("PROPUESTA", p, c);
      if (a.valor == null) {
        exigirSinPosteriores("PROPUESTA", p);
      } else {
        if (!Number.isInteger(a.valor) || a.valor <= 0) throw new ErrorPostulacion("Escribe el valor de tu oferta en pesos.");
        if (p.valor != null && p.valor > 0 && a.valor > p.valor) {
          throw new ErrorPostulacion("Tu oferta supera el presupuesto oficial: la rechazarían. Revisa tus costos.");
        }
      }
      return { valorOfertado: a.valor };
    }
    case "presentar": {
      exigir("PRESENTAR", p, c);
      if (a.valor) {
        if (cerrado(p, ahora)) throw new ErrorPostulacion("El proceso ya cerró: SECOP II ya no recibe ofertas.");
        return { presentadaAt: new Date(ahora).toISOString() };
      }
      exigirSinPosteriores("PRESENTAR", p);
      return { presentadaAt: null };
    }
    case "resultado": {
      exigir("EVALUACION", p, c);
      let plazo = a.plazo ?? null;
      if (a.resultado === "SUBSANAR") {
        if (plazo == null || !/^\d{4}-\d{2}-\d{2}$/.test(plazo)) throw new ErrorPostulacion("Indica hasta cuándo tienes para subsanar.");
        if (plazo < hoy) throw new ErrorPostulacion("El plazo para subsanar ya pasó.");
      } else if (a.resultado != null && a.resultado !== "GANADA" && a.resultado !== "NO_GANADA") {
        throw new ErrorPostulacion("Resultado no válido.");
      } else {
        plazo = null;
      }
      return { resultado: a.resultado, plazoSubsanar: plazo };
    }
    case "desistir":
      return { desistida: a.valor === true };
  }
}
