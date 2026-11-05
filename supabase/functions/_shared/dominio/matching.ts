// Puntaje de un proceso frente a un perfil (port de co.secopradar.modelo.servicio.Matching).
// Debe dar exactamente lo mismo que Java: lo comprueba matching.test.ts con
// los casos exportados desde Java (pruebas/vectores.json).
import { CATALOGO } from "./catalogo.generado.ts";
import { contiene, rangoPorId } from "./rangos.ts";
import { normalizar, palabras } from "./texto.ts";

export interface ProcesoMatch {
  id: string;
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
  /** ISO 8601 o null. */
  fechaCierre: string | null;
  ordenEntidad: string | null;
}

export interface PerfilMatch {
  categorias: string[];
  palabrasClave: string[];
  palabrasExcluir: string[];
  departamentos: string[];
  ciudades: string[];
  modalidades: string[];
  tiposContrato: string[];
  tiposEntidad: string[];
  rangosValor: string[];
  diasMinRestantes: number | null;
}

/** Puntos por nivel UNSPSC (largo del código del perfil). */
const PUNTOS_UNSPSC: Record<number, number> = { 6: 60, 4: 45, 2: 25 };
export const CODIGO_UNSPSC_VALIDO = /^\d{2}(\d{2}){0,2}$/;
const PESO_NOMBRE = 25;
const PESO_DESCRIPCION = 15;
const PESO_ENTIDAD = 10;
export const EXCLUIR_COMPRAS = "compras de bienes";
const INICIO_COMPRA = new Set(["suministro", "compra", "compraventa", "adquisicion", "adquirir"]);

export interface Textos {
  nombre: string[];
  descripcion: string[];
  entidad: string[];
}

// El mismo proceso se evalúa contra muchos perfiles: se tokeniza una vez.
const cache = new WeakMap<ProcesoMatch, Textos>();

export function textosDe(p: ProcesoMatch): Textos {
  let t = cache.get(p);
  if (!t) {
    t = { nombre: palabras(p.nombre), descripcion: palabras(p.objeto), entidad: palabras(p.entidad) };
    cache.set(p, t);
  }
  return t;
}

export function puntosCategoria(codigosPerfil: string[] | null, categoriaProceso: string | null): number {
  const codigo = categoriaProceso == null ? "" : categoriaProceso.split("V1.").join("");
  if (!/^\d{8}$/.test(codigo)) return 0;
  if (codigosPerfil == null) return 0;
  let mejor = 0;
  for (const c of codigosPerfil) {
    const puntos = PUNTOS_UNSPSC[c.length] ?? 0;
    if (puntos > mejor && codigo.startsWith(c)) mejor = puntos;
  }
  return mejor;
}

/** Misma palabra aceptando plural simple: dato/datos, red/redes, luz/luces. */
export function mismaPalabra(a: string, b: string): boolean {
  if (a === b) return true;
  const corta = a.length < b.length ? a : b;
  const larga = a.length < b.length ? b : a;
  if (larga === corta + "s" || larga === corta + "es") return true;
  return corta.endsWith("z") && larga === corta.substring(0, corta.length - 1) + "ces";
}

/** La frase debe aparecer completa y en orden, palabra por palabra. */
export function contieneFrase(texto: string[], frase: string[]): boolean {
  if (frase.length === 0 || frase.length > texto.length) return false;
  for (let i = 0; i <= texto.length - frase.length; i++) {
    let j = 0;
    while (j < frase.length && mismaPalabra(texto[i + j], frase[j])) j++;
    if (j === frase.length) return true;
  }
  return false;
}

function diceServicio(ps: string[]): boolean {
  return ps.some((w) => mismaPalabra(w, "servicio"));
}

function esCompraDeBienes(t: Textos): boolean {
  const primera = t.nombre.length > 0 ? t.nombre[0] : t.descripcion.length > 0 ? t.descripcion[0] : null;
  if (primera == null || !INICIO_COMPRA.has(primera)) return false;
  return !diceServicio(t.nombre) && !diceServicio(t.descripcion);
}

export function puntosFrase(t: Textos, frase: string[]): number {
  if (contieneFrase(t.nombre, frase)) return PESO_NOMBRE;
  if (contieneFrase(t.descripcion, frase)) return PESO_DESCRIPCION;
  return 0;
}

function vacia(l: string[] | null | undefined): boolean {
  return l == null || l.length === 0;
}

/** Valores de las listas del formulario (normalizados): esos se comparan exactos. */
const VALORES_DE_CATALOGO = new Set<string>(
  [
    ...CATALOGO.departamentos,
    ...CATALOGO.tiposContrato,
    ...CATALOGO.tiposEntidad,
    ...CATALOGO.modalidadesCompetitivas,
    ...CATALOGO.regiones.flatMap((r) => r.departamentos),
  ].map(normalizar),
);

/**
 * El valor del proceso está en la lista del perfil. Las opciones de la lista
 * se comparan exactas (sin tildes ni mayúsculas); lo escrito en "Otro" basta
 * con que sea una frase del valor.
 */
export function incluye(lista: string[], valor: string | null): boolean {
  if (valor == null) return false;
  if (lista.includes(valor)) return true;
  const normalizado = normalizar(valor);
  const palabrasValor = palabras(valor);
  for (const elegido of lista) {
    const e = normalizar(elegido);
    if (e === "") continue;
    if (e === normalizado) return true;
    if (!VALORES_DE_CATALOGO.has(e) && contieneFrase(palabrasValor, palabras(elegido))) return true;
  }
  return false;
}

/** Puntaje, o null si no cumple los filtros duros o no tiene señal temática. */
export function calcularScore(perfil: PerfilMatch, proceso: ProcesoMatch, ahoraMs: number = Date.now()): number | null {
  const textos = textosDe(proceso);
  const dias = proceso.fechaCierre == null ? null : (Date.parse(proceso.fechaCierre) - ahoraMs) / 86_400_000;

  if (!vacia(perfil.departamentos) && !incluye(perfil.departamentos, proceso.departamento)) return null;
  if (!vacia(perfil.ciudades) && !incluye(perfil.ciudades, proceso.ciudad)) return null;
  if (!vacia(perfil.modalidades) && !incluye(perfil.modalidades, proceso.modalidad)) return null;
  if (!vacia(perfil.tiposContrato) && !incluye(perfil.tiposContrato, proceso.tipoContrato)) return null;
  if (!vacia(perfil.tiposEntidad) && !incluye(perfil.tiposEntidad, proceso.ordenEntidad)) return null;

  // Procesos en $0 o sin informar nunca se descartan por rango de valor.
  const valor = proceso.valor == null ? 0 : proceso.valor;
  if (!vacia(perfil.rangosValor) && valor > 0) {
    const enRango = perfil.rangosValor.some((id) => {
      const r = rangoPorId(id);
      return r != null && contiene(r, valor);
    });
    if (!enRango) return null;
  }

  if (perfil.diasMinRestantes != null && perfil.diasMinRestantes > 0 && dias != null) {
    if (dias < perfil.diasMinRestantes) return null;
  }

  if (perfil.palabrasExcluir != null) {
    for (const palabra of perfil.palabrasExcluir) {
      if (normalizar(palabra) === EXCLUIR_COMPRAS) {
        if (esCompraDeBienes(textos)) return null;
      } else if (puntosFrase(textos, palabras(palabra)) > 0) {
        return null;
      }
    }
  }

  let score = puntosCategoria(perfil.categorias, proceso.categoria);
  let puntosTema = 0;
  let puntosEntidad = 0;
  if (perfil.palabrasClave != null) {
    for (const palabra of perfil.palabrasClave) {
      const frase = palabras(palabra);
      const puntos = puntosFrase(textos, frase);
      if (puntos > 0) puntosTema += puntos;
      else if (contieneFrase(textos.entidad, frase)) puntosEntidad += PESO_ENTIDAD;
    }
  }
  score += puntosTema;
  if (puntosTema > 0) score += puntosEntidad;

  const sinCriterio = vacia(perfil.categorias) && vacia(perfil.palabrasClave);
  if (score === 0 && !sinCriterio) return null;
  if (sinCriterio) score = 10;

  if (proceso.estadoApertura === "Abierto") score += 15;
  if (dias != null && dias > 0 && dias <= 7) score += 10;
  return score;
}
