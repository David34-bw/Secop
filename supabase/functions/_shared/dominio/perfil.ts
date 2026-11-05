// Validación de un perfil que llega de la app (ya armado por PerfilesService.aPerfil).
// El servidor no confía en la app: revisa tamaños, códigos y rangos.
import { CODIGO_UNSPSC_VALIDO, type PerfilMatch } from "./matching.ts";
import { rangoPorId } from "./rangos.ts";

const MAX_ITEMS = 60;
const MAX_TEXTO = 200;

export interface PerfilEntrada extends PerfilMatch {
  nombre: string;
  profesionId: string | null;
}

function lista(v: unknown, campo: string, errores: string[]): string[] {
  if (v == null) return [];
  if (!Array.isArray(v)) {
    errores.push(campo + " debe ser una lista");
    return [];
  }
  const l: string[] = [];
  for (const x of v) {
    if (typeof x !== "string") {
      errores.push(campo + " solo puede tener textos");
      return [];
    }
    const s = x.trim();
    if (s !== "" && s.length <= MAX_TEXTO && !l.includes(s) && l.length < MAX_ITEMS) l.push(s);
  }
  return l;
}

/** Devuelve el perfil limpio o la lista de errores. */
export function validarPerfil(e: Record<string, unknown>): { perfil: PerfilEntrada | null; errores: string[] } {
  const errores: string[] = [];
  const nombre = typeof e.nombre === "string" ? e.nombre.trim() : "";
  if (nombre === "") errores.push("Ponle un nombre al perfil");
  const categorias = lista(e.categorias, "categorias", errores);
  if (categorias.some((c) => !CODIGO_UNSPSC_VALIDO.test(c))) errores.push("Hay categorías UNSPSC no válidas");
  const rangosValor = lista(e.rangosValor, "rangosValor", errores);
  if (rangosValor.some((r) => rangoPorId(r) == null)) errores.push("Hay rangos de valor no válidos");
  let dias: number | null = null;
  if (e.diasMinRestantes != null) {
    if (!Number.isInteger(e.diasMinRestantes) || (e.diasMinRestantes as number) < 0 || (e.diasMinRestantes as number) > 365) {
      errores.push("Días mínimos no válidos");
    } else if ((e.diasMinRestantes as number) > 0) {
      dias = e.diasMinRestantes as number;
    }
  }
  const profesionId = typeof e.profesionId === "string" && e.profesionId.length <= 60 ? e.profesionId : null;
  const perfil: PerfilEntrada = {
    nombre: nombre.length > MAX_TEXTO ? nombre.substring(0, MAX_TEXTO) : nombre,
    profesionId,
    categorias,
    palabrasClave: lista(e.palabrasClave, "palabrasClave", errores),
    palabrasExcluir: lista(e.palabrasExcluir, "palabrasExcluir", errores),
    departamentos: lista(e.departamentos, "departamentos", errores),
    ciudades: lista(e.ciudades, "ciudades", errores),
    modalidades: lista(e.modalidades, "modalidades", errores),
    tiposContrato: lista(e.tiposContrato, "tiposContrato", errores),
    tiposEntidad: lista(e.tiposEntidad, "tiposEntidad", errores),
    rangosValor,
    diasMinRestantes: dias,
  };
  return errores.length > 0 ? { perfil: null, errores } : { perfil, errores };
}
