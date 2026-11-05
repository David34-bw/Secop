// Reglas del servidor que no tienen vector exportado de Java: etapas,
// oportunidades visibles, perfiles y ajuste de planes.
import { test } from "node:test";
import assert from "node:assert/strict";
import { aplicar, avance, ErrorPostulacion, type Postulacion } from "../etapas.ts";
import { panelVisible, type FilaOportunidad } from "../oportunidades.ts";
import { validarPerfil } from "../perfil.ts";
import { cambiosPorPlan, planEfectivo } from "../plan.ts";

const AHORA = Date.parse("2026-11-05T15:00:00Z");
const HOY = "2026-11-05";

function post(cambios: Partial<Postulacion> = {}): Postulacion {
  return {
    modalidad: "Mínima cuantía", valor: 10_000_000, fechaCierre: "2026-11-15T04:59:59Z", revisado: false,
    pliegoLeido: false, requisitosExtra: [], valorOfertado: null, presentadaAt: null, resultado: null,
    plazoSubsanar: null, desistida: false, ...cambios,
  };
}
const conDocs = { registradoSecop: true, tiposVigentes: new Set(["CEDULA", "INHABILIDADES", "SEGURIDAD_SOCIAL"]) };
const sinNada = { registradoSecop: false, tiposVigentes: new Set<string>() };

test("Etapas: empiezan en el registro y van en orden", () => {
  assert.equal(avance(post(), sinNada).actual, "REGISTRO");
  assert.throws(() => aplicar({ accion: "revisar", valor: true }, post(), sinNada, HOY, AHORA), ErrorPostulacion);
  assert.deepEqual(aplicar({ accion: "revisar", valor: true }, post(), conDocs, HOY, AHORA), { revisado: true });
  assert.throws(() => aplicar({ accion: "propuesta", valor: 1000 }, post({ revisado: true }), conDocs, HOY, AHORA),
    /Primero completa la etapa 3/);
});

test("Etapas: documentos automáticos más requisitos del pliego", () => {
  const p = post({ revisado: true, pliegoLeido: true, requisitosExtra: [{ texto: "Curso de alturas", cumplido: false }] });
  assert.deepEqual(avance(p, conDocs).faltantesDocumentos, ["Curso de alturas"]);
  assert.equal(avance(p, conDocs).actual, "DOCUMENTOS");
  const r = aplicar({ accion: "requisitoAlternar", indice: 0 }, p, conDocs, HOY, AHORA);
  assert.equal(avance({ ...p, ...r }, conDocs).actual, "PROPUESTA");
  assert.throws(() => aplicar({ accion: "requisitoAgregar", texto: "curso DE alturas" }, p, conDocs, HOY, AHORA), /ya está/);
  assert.throws(() => aplicar({ accion: "requisitoQuitar", indice: 5 }, p, conDocs, HOY, AHORA), /no existe/);
});

test("Etapas: oferta, presentación y resultado", () => {
  const p = post({ revisado: true, pliegoLeido: true });
  assert.throws(() => aplicar({ accion: "propuesta", valor: 10_000_001 }, p, conDocs, HOY, AHORA), /presupuesto/);
  assert.deepEqual(aplicar({ accion: "propuesta", valor: 9_000_000 }, p, conDocs, HOY, AHORA), { valorOfertado: 9_000_000 });
  const conOferta = { ...p, valorOfertado: 9_000_000 };
  assert.throws(() => aplicar({ accion: "pliego", valor: false }, conOferta, conDocs, HOY, AHORA), /desmarca la etapa 5/);
  const cerrado = { ...conOferta, fechaCierre: "2026-11-01T04:59:59Z" };
  assert.throws(() => aplicar({ accion: "presentar", valor: true }, cerrado, conDocs, HOY, AHORA), /ya cerró/);
  const presentada = { ...conOferta, ...aplicar({ accion: "presentar", valor: true }, conOferta, conDocs, HOY, AHORA) };
  assert.ok(presentada.presentadaAt);
  assert.throws(() => aplicar({ accion: "resultado", resultado: "SUBSANAR" }, presentada, conDocs, HOY, AHORA), /hasta cuándo/);
  assert.throws(() => aplicar({ accion: "resultado", resultado: "SUBSANAR", plazo: "2026-11-01" }, presentada, conDocs, HOY, AHORA), /ya pasó/);
  const ganada = { ...presentada, ...aplicar({ accion: "resultado", resultado: "GANADA" }, presentada, conDocs, HOY, AHORA) };
  assert.equal(avance(ganada, conDocs).actual, null);
  assert.throws(() => aplicar({ accion: "revisar", valor: true }, { ...p, desistida: true }, conDocs, HOY, AHORA), /Desististe/);
});

function fila(id: number, procesoId: string, score: number, extra: Partial<FilaOportunidad["proceso"]> = {}): FilaOportunidad {
  return {
    id, score, notificado: false, perfilId: 1, perfil: "P",
    proceso: { id: procesoId, entidad: "E" + procesoId, objeto: "O" + procesoId, valor: 1, fechaCierre: "2026-11-20T00:00:00Z", fechaPublicacion: "2026-11-01T00:00:00Z", ...extra },
  };
}

test("Oportunidades: duplicados, ocultos y límite del plan", () => {
  const dup = fila(4, "P1b", 40, { entidad: "EP1", objeto: "OP1", fechaPublicacion: "2026-11-03T00:00:00Z" });
  const filas = [fila(1, "P1", 50), fila(2, "P2", 70), fila(3, "P3", 60), dup];
  const sinLimite = panelVisible(filas, new Set(), false, null);
  assert.deepEqual(sinLimite.filas.map((f) => f.proceso.id), ["P2", "P3", "P1b"]);
  const conOculto = panelVisible(filas, new Set(["P1"]), false, null);
  assert.deepEqual(conOculto.filas.map((f) => f.proceso.id), ["P2", "P3"], "el duplicado también se oculta");
  assert.equal(conOculto.ocultasPorUsuario, 1);
  const limite = panelVisible(filas, new Set(), false, 2);
  assert.deepEqual(limite.filas.map((f) => f.proceso.id), ["P2", "P3"]);
  assert.equal(limite.ocultasPorPlan, 1);
  assert.equal(panelVisible(filas, new Set(["P3"]), false, 2).ocultasPorPlan, 0, "los ocultos no gastan el plan");
});

test("Perfil: el servidor limpia y rechaza datos malos", () => {
  const ok = validarPerfil({ nombre: " Software ", categorias: ["43", "4321"], palabrasClave: ["software", "software", " "], rangosValor: ["menos_10m", "otro:5-"] });
  assert.deepEqual(ok.errores, []);
  assert.deepEqual(ok.perfil!.palabrasClave, ["software"]);
  assert.equal(ok.perfil!.nombre, "Software");
  const malo = validarPerfil({ nombre: "", categorias: ["4"], rangosValor: ["x"], diasMinRestantes: -1, palabrasClave: "no es lista" });
  assert.equal(malo.perfil, null);
  assert.equal(malo.errores.length, 5);
});

test("Planes: cambios de perfiles por plan", () => {
  const gratis = planEfectivo({ rol: "usuario", plan: "gratis", pruebaHasta: null, graciaHasta: null, prepagoPlan: null, prepagoHasta: null });
  const perfiles = [
    { id: 1, activo: true, desactivadoPorPlan: false },
    { id: 2, activo: true, desactivadoPorPlan: false },
    { id: 3, activo: false, desactivadoPorPlan: true },
  ];
  assert.deepEqual(cambiosPorPlan(perfiles, gratis), [{ id: 2, activo: false, desactivadoPorPlan: true }]);
  const empresa = planEfectivo({ rol: "usuario", plan: "empresa", pruebaHasta: null, graciaHasta: null, prepagoPlan: null, prepagoHasta: null });
  assert.deepEqual(cambiosPorPlan(perfiles, empresa), [{ id: 3, activo: true, desactivadoPorPlan: false }]);
});
