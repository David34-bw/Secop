// Datos del proponente: validación y limpieza (port de DocumentosService.guardarDatos).

export interface Proponente {
  tipoPersona: string | null;
  tipoDocumento: string | null;
  numeroDocumento: string | null;
  telefono: string | null;
  departamento: string | null;
  ciudad: string | null;
  profesion: string | null;
  titulo: string | null;
  universidad: string | null;
  matriculaProfesional: string | null;
  aniosExperiencia: number | null;
  resumen: string | null;
  registradoSecop: boolean;
  tieneRup: boolean;
  numeroRup: string | null;
  sigepActualizada: boolean;
}

export const TIPOS_PERSONA = ["Persona natural", "Persona jurídica"];
export const TIPOS_DOCUMENTO = [
  "Cédula de ciudadanía", "Cédula de extranjería", "NIT", "Permiso por Protección Temporal (PPT)", "Pasaporte",
];

/** trim de Java + espacios ASCII repetidos a uno; vacío = null. */
function limpiar(s: string | null | undefined): string | null {
  if (s == null) return null;
  let i = 0, j = s.length;
  while (i < j && s.charCodeAt(i) <= 0x20) i++;
  while (j > i && s.charCodeAt(j - 1) <= 0x20) j--;
  const t = s.substring(i, j).replace(/[ \t\n\x0B\f\r]+/g, " ");
  return t === "" ? null : t;
}

export function vacio(): Proponente {
  return {
    tipoPersona: "Persona natural", tipoDocumento: "Cédula de ciudadanía", numeroDocumento: null, telefono: null,
    departamento: null, ciudad: null, profesion: null, titulo: null, universidad: null, matriculaProfesional: null,
    aniosExperiencia: null, resumen: null, registradoSecop: false, tieneRup: false, numeroRup: null, sigepActualizada: false,
  };
}

/** Errores por campo (vacío si es válido) y los datos limpios para guardar. */
export function validarProponente(p: Proponente): { errores: Record<string, string>; limpio: Proponente } {
  const errores: Record<string, string> = {};
  let documento = limpiar(p.numeroDocumento);
  if (documento != null) {
    documento = documento.replaceAll(".", "").replaceAll(" ", "");
    const esNit = p.tipoDocumento === "NIT";
    const pasaporte = p.tipoDocumento != null && p.tipoDocumento.startsWith("Pasaporte");
    const ok = pasaporte ? /^[A-Za-z0-9]{5,15}$/.test(documento)
      : esNit ? /^\d{6,12}(-\d)?$/.test(documento)
      : /^\d{5,12}$/.test(documento);
    if (!ok) {
      errores.numeroDocumento = esNit
        ? "El NIT debe tener entre 6 y 12 números (puede llevar el dígito de verificación: 900123456-7)."
        : pasaporte ? "El pasaporte solo lleva letras y números (5 a 15)."
        : "El número de documento solo lleva números (entre 5 y 12), sin puntos.";
    }
  }
  let telefono = limpiar(p.telefono);
  if (telefono != null) {
    telefono = telefono.replace(/[ \t\n\x0B\f\r()-]/g, "").replace(/^\+?57/, "");
    if (!/^(3\d{9}|60\d{8}|\d{7})$/.test(telefono)) {
      errores.telefono = "Escribe un celular de 10 dígitos (empieza por 3) o un fijo (60 + 8 dígitos).";
    }
  }
  const ciudad = limpiar(p.ciudad);
  if (ciudad != null && !/^[\p{L} .'-]{2,60}$/u.test(ciudad)) errores.ciudad = "La ciudad solo lleva letras.";
  const anios = p.aniosExperiencia;
  if (anios != null && (!Number.isInteger(anios) || anios < 0 || anios > 60)) {
    errores.aniosExperiencia = "Los años de experiencia van de 0 a 60.";
  }
  const matricula = limpiar(p.matriculaProfesional);
  if (matricula != null && !/^[A-Za-z0-9 -]{4,30}$/.test(matricula)) {
    errores.matriculaProfesional = "La matrícula solo lleva letras, números y guiones (ej: 25202-123456).";
  }
  const rup = p.tieneRup ? limpiar(p.numeroRup) : null;
  if (rup != null && !/^[A-Za-z0-9 -]{3,30}$/.test(rup)) {
    errores.numeroRup = "El número de inscripción del RUP solo lleva letras, números y guiones.";
  }
  const resumen = limpiar(p.resumen);
  if (resumen != null && resumen.length > 2000) errores.resumen = "El perfil profesional puede tener hasta 2.000 caracteres.";
  for (const [campo, valor] of [["profesion", p.profesion], ["titulo", p.titulo], ["universidad", p.universidad]] as const) {
    const v = limpiar(valor);
    if (v != null && (v.length > 150 || !/\p{L}/u.test(v))) errores[campo] = "Escribe un texto válido (hasta 150 caracteres).";
  }
  // El servidor también acota los textos libres y las listas cerradas.
  if (p.tipoPersona != null && !TIPOS_PERSONA.includes(p.tipoPersona)) errores.tipoPersona = "Tipo de persona no válido.";
  if (p.tipoDocumento != null && !TIPOS_DOCUMENTO.includes(p.tipoDocumento)) errores.tipoDocumento = "Tipo de documento no válido.";
  const departamento = limpiar(p.departamento);
  if (departamento != null && departamento.length > 100) errores.departamento = "Departamento no válido.";

  const limpio: Proponente = {
    tipoPersona: p.tipoPersona, tipoDocumento: p.tipoDocumento, numeroDocumento: documento, telefono,
    departamento, ciudad, profesion: limpiar(p.profesion), titulo: limpiar(p.titulo),
    universidad: limpiar(p.universidad), matriculaProfesional: matricula, aniosExperiencia: anios, resumen,
    registradoSecop: p.registradoSecop === true, tieneRup: p.tieneRup === true, numeroRup: rup,
    sigepActualizada: p.sigepActualizada === true,
  };
  return { errores, limpio };
}
