// Paridad con Java: los casos de vectores.json los generó Java
// (scratchpad ExportarParidad) con la lógica de la app de escritorio.
// Correr con: node --test supabase/functions/_shared/dominio/pruebas/
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { calcularScore, type PerfilMatch, type ProcesoMatch } from "../matching.ts";
import { evaluarPassword, formatearNombre, validarFormatoCorreo, validarNombre, limpiarCorreo } from "../validadores.ts";
import { planEfectivo } from "../plan.ts";
import { fechaColombia } from "../ingesta.ts";
import { validarProponente } from "../proponente.ts";
import { grupoDeModalidad } from "../preparacion.ts";

const v = JSON.parse(readFileSync(new URL("./vectores.json", import.meta.url), "utf8"));

test("Matching: mismos puntajes que Java", () => {
  const ahora = Date.parse(v.ahora);
  const procesos: ProcesoMatch[] = v.procesos;
  let comparados = 0;
  for (const caso of v.matching) {
    const perfil: PerfilMatch = caso.perfil;
    const obtenidos: Record<string, number> = {};
    for (const p of procesos) {
      const s = calcularScore(perfil, p, ahora);
      if (s != null) obtenidos[p.id] = s;
    }
    assert.deepEqual(obtenidos, caso.scores, "perfil " + JSON.stringify(perfil.palabrasClave.slice(0, 3)));
    comparados += Object.keys(caso.scores).length;
  }
  assert.ok(comparados > 500, "pocos casos comparados: " + comparados);
});

test("Nombre: mismos errores y formato que Java", () => {
  for (const c of v.nombres) {
    assert.equal(validarNombre(c.entrada), c.error, JSON.stringify(c.entrada));
    assert.equal(formatearNombre(c.entrada), c.formateado, JSON.stringify(c.entrada));
  }
});

test("Contraseña: mismas reglas y nivel que Java", () => {
  for (const c of v.passwords) {
    const r = evaluarPassword(c.entrada, v.passwordEmail, v.passwordNombre);
    assert.deepEqual(r.reglas.map((x) => x.cumple), c.reglas, JSON.stringify(c.entrada));
    assert.equal(r.nivel, c.nivel, JSON.stringify(c.entrada));
  }
});

test("Correo: mismo formato que Java", () => {
  for (const c of v.correos) assert.equal(validarFormatoCorreo(limpiarCorreo(c.entrada)), c.error, JSON.stringify(c.entrada));
});

test("Plan efectivo: igual que Java", () => {
  const ahora = Date.parse(v.ahora);
  for (const c of v.planes) {
    const p = planEfectivo(c.usuario, ahora);
    assert.deepEqual(
      { id: p.id, origen: p.origen, maxPerfiles: p.maxPerfiles, maxCoincidencias: p.maxCoincidencias, diasRestantes: p.diasRestantes },
      { id: c.id, origen: c.origen, maxPerfiles: c.maxPerfiles, maxCoincidencias: c.maxCoincidencias, diasRestantes: c.diasRestantes },
      JSON.stringify(c.usuario),
    );
  }
});

test("Fechas de SECOP: igual que Java", () => {
  for (const c of v.fechas) {
    const f = fechaColombia(c.entrada, c.cierre);
    assert.equal(f == null ? null : new Date(f).toISOString().replace(".000Z", "Z"), c.instante, c.entrada + " " + c.cierre);
  }
});

test("Proponente: mismos errores y limpieza que Java", () => {
  for (const c of v.proponentes) {
    const r = validarProponente(c.entrada);
    if (c.errores == null) {
      assert.deepEqual(r.errores, {}, JSON.stringify(c.entrada));
      assert.deepEqual(r.limpio, c.guardado);
    } else {
      assert.deepEqual(Object.keys(r.errores).sort(), [...c.errores].sort(), JSON.stringify(c.entrada));
    }
  }
});

test("Grupo de modalidad: igual que Java", () => {
  for (const c of v.gruposModalidad) assert.equal(grupoDeModalidad(c.modalidad) ?? "", c.grupo, c.modalidad);
});
