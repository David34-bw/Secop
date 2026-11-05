// Plan que rige para un usuario y ajuste de sus perfiles al plan
// (port de co.secopradar.modelo.Plan y PlanesService.sincronizarPerfiles).
import { CATALOGO } from "./catalogo.generado.ts";

export interface Plan {
  id: string;
  nombre: string;
  maxPerfiles: number | null;
  maxCoincidencias: number | null;
  alertas: boolean;
  /** admin | pago | gracia | prepago | prueba | gratis */
  origen: string;
  /** ISO o null. */
  hasta: string | null;
  diasRestantes: number | null;
}

export interface UsuarioPlan {
  rol: string;
  plan: string | null;
  pruebaHasta: string | null;
  graciaHasta: string | null;
  prepagoPlan: string | null;
  prepagoHasta: string | null;
}

const PLANES = new Map(CATALOGO.planes.map((p) => [p.id, p]));
const RANGO: Record<string, number> = { gratis: 0, profesional: 1, empresa: 2 };
const DE_PAGO = new Set(["profesional", "empresa"]);

function con(id: string, origen: string, hasta: string | null, diasRestantes: number | null): Plan {
  const p = PLANES.get(id)!;
  return {
    id: p.id, nombre: p.nombre, maxPerfiles: p.maxPerfiles, maxCoincidencias: p.maxCoincidencias, alertas: p.alertas,
    origen, hasta, diasRestantes,
  };
}

const vigente = (f: string | null, ahora: number) => f != null && Date.parse(f) > ahora;
const esDePago = (p: string | null) => p != null && DE_PAGO.has(p);

/** Igual que Plan.efectivo en Java: se calcula en cada consulta. */
export function planEfectivo(u: UsuarioPlan, ahora: number = Date.now()): Plan {
  if (u.rol === "admin") {
    return { id: "admin", nombre: "Administrador", maxPerfiles: null, maxCoincidencias: null, alertas: true, origen: "admin", hasta: null, diasRestantes: null };
  }
  let porSuscripcion: Plan | null = null;
  if (esDePago(u.plan)) {
    if (u.graciaHasta == null) porSuscripcion = con(u.plan!, "pago", null, null);
    else if (vigente(u.graciaHasta, ahora)) porSuscripcion = con(u.plan!, "gracia", u.graciaHasta, null);
  }
  const porPrepago = esDePago(u.prepagoPlan) && vigente(u.prepagoHasta, ahora)
    ? con(u.prepagoPlan!, "prepago", u.prepagoHasta, null)
    : null;
  if (porSuscripcion || porPrepago) {
    if (!porPrepago) return porSuscripcion!;
    if (!porSuscripcion) return porPrepago;
    return RANGO[porPrepago.id] > RANGO[porSuscripcion.id] ? porPrepago : porSuscripcion;
  }
  if (vigente(u.pruebaHasta, ahora)) {
    const dias = Math.ceil((Date.parse(u.pruebaHasta!) - ahora) / 86_400_000);
    return con(CATALOGO.planDePrueba, "prueba", u.pruebaHasta, dias);
  }
  return con("gratis", "gratis", null, null);
}

export function mensajeLimitePerfiles(plan: Plan): string {
  const n = plan.maxPerfiles!;
  return "Tu plan " + plan.nombre + " permite " + n + " perfil" + (n === 1 ? "" : "es") + " activo" + (n === 1 ? "" : "s")
    + ". Elimina uno o mejora tu plan para crear otro.";
}

export interface PerfilEstado {
  id: number;
  activo: boolean;
  desactivadoPorPlan: boolean;
}

export interface CambioEstado {
  id: number;
  activo: boolean;
  desactivadoPorPlan: boolean;
}

/**
 * Qué perfiles cambiar para que queden a lo sumo maxPerfiles activos
 * (perfiles en orden de creación). Igual que PlanesService.sincronizarPerfiles.
 */
export function cambiosPorPlan(perfiles: PerfilEstado[], plan: Plan): CambioEstado[] {
  const cambios: CambioEstado[] = [];
  if (plan.maxPerfiles == null) {
    for (const p of perfiles) if (p.desactivadoPorPlan) cambios.push({ id: p.id, activo: true, desactivadoPorPlan: false });
    return cambios;
  }
  let activos = 0;
  for (const p of perfiles) {
    if (!p.activo) continue;
    activos++;
    if (activos > plan.maxPerfiles) cambios.push({ id: p.id, activo: false, desactivadoPorPlan: true });
  }
  let cupo = Math.max(plan.maxPerfiles - Math.min(activos, plan.maxPerfiles), 0);
  for (const p of perfiles) {
    if (cupo === 0) break;
    if (p.desactivadoPorPlan && !p.activo) {
      cambios.push({ id: p.id, activo: true, desactivadoPorPlan: false });
      cupo--;
    }
  }
  return cambios;
}
