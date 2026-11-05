// Lo que ve cada usuario: sin duplicados, sin ocultos y con el límite del
// plan aplicado en el servidor (port de OportunidadesService.sinDuplicados,
// visibles y oculta). Filtros y orden de la pantalla siguen en la app.

export interface FilaOportunidad {
  id: number;
  score: number;
  notificado: boolean;
  perfilId: number;
  perfil: string;
  proceso: {
    id: string;
    entidad: string | null;
    objeto: string | null;
    valor: number | null;
    fechaCierre: string | null;
    fechaPublicacion: string | null;
  };
}

/** Mismo proceso publicado dos veces o encontrado por dos perfiles. */
function clave(f: FilaOportunidad): string {
  const p = f.proceso;
  const cierre = p.fechaCierre == null ? null : Date.parse(p.fechaCierre);
  return JSON.stringify([p.entidad, p.objeto, p.valor, cierre]);
}

const cmp = (a: string, b: string) => (a < b ? -1 : a > b ? 1 : 0);

/** De cada grupo de duplicados queda la publicación más reciente. */
export function sinDuplicados<T extends FilaOportunidad>(filas: T[]): T[] {
  const masReciente = (a: T, b: T): number => {
    const fa = a.proceso.fechaPublicacion == null ? null : Date.parse(a.proceso.fechaPublicacion);
    const fb = b.proceso.fechaPublicacion == null ? null : Date.parse(b.proceso.fechaPublicacion);
    if (fa !== fb) {
      if (fa == null) return -1;
      if (fb == null) return 1;
      return fa < fb ? -1 : 1;
    }
    const c = cmp(a.proceso.id, b.proceso.id);
    return c !== 0 ? c : a.id - b.id;
  };
  const mejor = new Map<string, T>();
  for (const f of filas) {
    const k = clave(f);
    const actual = mejor.get(k);
    mejor.set(k, actual == null ? f : masReciente(actual, f) >= 0 ? actual : f);
  }
  return [...mejor.values()];
}

export function porRelevancia(a: FilaOportunidad, b: FilaOportunidad): number {
  if (a.score !== b.score) return b.score - a.score;
  const ca = a.proceso.fechaCierre == null ? null : Date.parse(a.proceso.fechaCierre);
  const cb = b.proceso.fechaCierre == null ? null : Date.parse(b.proceso.fechaCierre);
  if (ca !== cb) {
    if (ca == null) return 1;
    if (cb == null) return -1;
    return ca - cb;
  }
  return a.id - b.id;
}

/** Filas ocultas por el usuario: por la clave, para que no reaparezcan con otro id. */
export function predicadoOculta<T extends FilaOportunidad>(filas: T[], idsOcultos: Set<string>): (f: T) => boolean {
  const claves = new Set(filas.filter((f) => idsOcultos.has(f.proceso.id)).map(clave));
  return (f) => claves.has(clave(f));
}

export function panelVisible<T extends FilaOportunidad>(
  todas: T[], idsOcultos: Set<string>, incluirOcultas: boolean, maxCoincidencias: number | null,
) {
  const oculta = predicadoOculta(todas, idsOcultos);
  const sinRepetir = sinDuplicados(todas);
  const ocultasPorUsuario = sinRepetir.filter(oculta).length;
  const procesosOcultos = sinRepetir.filter(oculta).map((f) => f.proceso.id);
  const vigentes = incluirOcultas ? sinRepetir : sinRepetir.filter((f) => !oculta(f));
  const orden = [...vigentes].sort(porRelevancia);
  const filas = maxCoincidencias == null ? orden : orden.slice(0, maxCoincidencias);
  const ocultasPorPlan = maxCoincidencias == null ? 0 : Math.max(vigentes.length - maxCoincidencias, 0);
  return { filas, ocultasPorPlan, ocultasPorUsuario, procesosOcultos };
}
