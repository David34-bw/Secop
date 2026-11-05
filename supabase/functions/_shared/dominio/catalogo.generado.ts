// GENERADO desde Java (ExportarParidad). No editar a mano: si cambia el catálogo en Java,
// vuelve a generarlo para que el servidor y la app usen los mismos valores.
export const CATALOGO = {
  "departamentos" : [ "Distrito Capital de Bogotá", "Antioquia", "Valle del Cauca", "Cundinamarca", "Atlántico", "Santander", "Bolívar", "Boyacá", "Nariño", "Córdoba", "Tolima", "Cauca", "Norte de Santander", "Magdalena", "Huila", "Caldas", "Risaralda", "Meta", "Cesar", "Sucre", "Quindío", "La Guajira", "Casanare", "Chocó", "Caquetá", "Putumayo", "Arauca" ],
  "tiposContrato" : [ "Prestación de servicios", "Suministros", "Compraventa", "Obra", "Interventoría", "Consultoría", "Seguros", "Arrendamiento de muebles", "Concesión", "Servicios financieros" ],
  "tiposEntidad" : [ "Nacional", "Territorial", "Corporación Autónoma" ],
  "modalidadesCompetitivas" : [ "Mínima cuantía", "Concurso de méritos abierto", "Licitación pública", "Licitación pública Obra Publica", "Selección Abreviada de Menor Cuantía", "Seleccion Abreviada Menor Cuantia Sin Manifestacion Interes", "Selección abreviada subasta inversa", "Contratación régimen especial (con ofertas)", "Solicitud de información a los Proveedores" ],
  "regiones" : [ {
    "departamentos" : [ "Antioquia", "Boyacá", "Caldas", "Cundinamarca", "Distrito Capital de Bogotá", "Huila", "Norte de Santander", "Quindío", "Risaralda", "Santander", "Tolima" ],
    "id" : "andina",
    "nombre" : "Andina"
  }, {
    "departamentos" : [ "Atlántico", "Bolívar", "Cesar", "Córdoba", "La Guajira", "Magdalena", "Sucre", "San Andrés, Providencia y Santa Catalina" ],
    "id" : "caribe",
    "nombre" : "Caribe"
  }, {
    "departamentos" : [ "Chocó", "Valle del Cauca", "Cauca", "Nariño" ],
    "id" : "pacifico",
    "nombre" : "Pacífico"
  }, {
    "departamentos" : [ "Arauca", "Casanare", "Meta", "Vichada" ],
    "id" : "orinoquia",
    "nombre" : "Orinoquía"
  }, {
    "departamentos" : [ "Amazonas", "Caquetá", "Guainía", "Guaviare", "Putumayo", "Vaupés" ],
    "id" : "amazonia",
    "nombre" : "Amazonía"
  } ],
  "rangosValor" : [ {
    "min" : 0,
    "id" : "menos_10m",
    "max" : 10000000
  }, {
    "min" : 10000000,
    "id" : "10_30m",
    "max" : 30000000
  }, {
    "min" : 30000000,
    "id" : "30_80m",
    "max" : 80000000
  }, {
    "min" : 80000000,
    "id" : "80_200m",
    "max" : 200000000
  }, {
    "min" : 200000000,
    "id" : "200_500m",
    "max" : 500000000
  }, {
    "min" : 500000000,
    "id" : "mas_500m",
    "max" : -1
  } ],
  "tiposDocumento" : [ {
    "grupo" : "BASICO",
    "nombre" : "Cédula de ciudadanía",
    "vence" : false,
    "varios" : false,
    "id" : "CEDULA"
  }, {
    "grupo" : "BASICO",
    "nombre" : "Declaración de inhabilidades",
    "vence" : false,
    "varios" : false,
    "id" : "INHABILIDADES"
  }, {
    "grupo" : "BASICO",
    "nombre" : "Seguridad social al día",
    "vence" : true,
    "varios" : false,
    "id" : "SEGURIDAD_SOCIAL"
  }, {
    "grupo" : "BASICO",
    "nombre" : "Certificados de antecedentes",
    "vence" : true,
    "varios" : true,
    "id" : "ANTECEDENTES"
  }, {
    "grupo" : "COMUN",
    "nombre" : "RUT",
    "vence" : false,
    "varios" : false,
    "id" : "RUT"
  }, {
    "grupo" : "COMUN",
    "nombre" : "Certificación bancaria",
    "vence" : true,
    "varios" : false,
    "id" : "CERTIFICACION_BANCARIA"
  }, {
    "grupo" : "MODALIDAD",
    "nombre" : "Certificado del RUP",
    "vence" : true,
    "varios" : false,
    "id" : "RUP"
  }, {
    "grupo" : "MODALIDAD",
    "nombre" : "Hoja de vida SIGEP II",
    "vence" : false,
    "varios" : false,
    "id" : "HOJA_VIDA_SIGEP"
  }, {
    "grupo" : "PROFESION",
    "nombre" : "Título profesional",
    "vence" : false,
    "varios" : true,
    "id" : "TITULO"
  }, {
    "grupo" : "PROFESION",
    "nombre" : "Matrícula o tarjeta profesional",
    "vence" : false,
    "varios" : false,
    "id" : "TARJETA_PROFESIONAL"
  }, {
    "grupo" : "PROFESION",
    "nombre" : "Certificados de experiencia",
    "vence" : false,
    "varios" : true,
    "id" : "EXPERIENCIA"
  }, {
    "grupo" : "PROFESION",
    "nombre" : "Certificaciones técnicas",
    "vence" : true,
    "varios" : true,
    "id" : "CERTIFICACION_TECNICA"
  }, {
    "grupo" : "OTROS",
    "nombre" : "Otros documentos",
    "vence" : false,
    "varios" : true,
    "id" : "OTRO"
  } ],
  "gruposModalidad" : [ {
    "nombre" : "Mínima cuantía",
    "id" : "MINIMA_CUANTIA",
    "pideRup" : false,
    "requeridos" : [ "CEDULA", "INHABILIDADES", "SEGURIDAD_SOCIAL" ]
  }, {
    "nombre" : "Selección abreviada / subasta inversa",
    "id" : "SELECCION_ABREVIADA",
    "pideRup" : true,
    "requeridos" : [ "CEDULA", "INHABILIDADES", "SEGURIDAD_SOCIAL", "RUP", "EXPERIENCIA" ]
  }, {
    "nombre" : "Licitación pública",
    "id" : "LICITACION",
    "pideRup" : true,
    "requeridos" : [ "CEDULA", "INHABILIDADES", "SEGURIDAD_SOCIAL", "RUP", "EXPERIENCIA" ]
  }, {
    "nombre" : "Concurso de méritos",
    "id" : "CONCURSO_MERITOS",
    "pideRup" : true,
    "requeridos" : [ "CEDULA", "INHABILIDADES", "SEGURIDAD_SOCIAL", "RUP", "EXPERIENCIA", "TITULO" ]
  }, {
    "nombre" : "Prestación de servicios (contratación directa)",
    "id" : "CONTRATACION_DIRECTA",
    "pideRup" : false,
    "requeridos" : [ "CEDULA", "INHABILIDADES", "SEGURIDAD_SOCIAL", "HOJA_VIDA_SIGEP", "TITULO", "EXPERIENCIA" ]
  } ],
  "etapas" : [ {
    "id" : "REGISTRO",
    "titulo" : "Registrarte en SECOP II"
  }, {
    "id" : "REVISAR",
    "titulo" : "Revisar el proceso"
  }, {
    "id" : "PLIEGO",
    "titulo" : "Leer la invitación o el pliego"
  }, {
    "id" : "DOCUMENTOS",
    "titulo" : "Reunir los documentos"
  }, {
    "id" : "PROPUESTA",
    "titulo" : "Preparar la propuesta económica"
  }, {
    "id" : "PRESENTAR",
    "titulo" : "Presentar la oferta"
  }, {
    "id" : "EVALUACION",
    "titulo" : "Revisar la evaluación"
  } ],
  "planes" : [ {
    "id" : "gratis",
    "nombre" : "Gratis",
    "maxPerfiles" : 1,
    "maxCoincidencias" : 5,
    "alertas" : false
  }, {
    "id" : "profesional",
    "nombre" : "Profesional",
    "maxPerfiles" : 2,
    "maxCoincidencias" : null,
    "alertas" : true
  }, {
    "id" : "empresa",
    "nombre" : "Empresa",
    "maxPerfiles" : 10,
    "maxCoincidencias" : null,
    "alertas" : true
  } ],
  "diasPrueba" : 30,
  "planDePrueba" : "profesional",
  "profesiones" : [ "arquitecto", "ingeniero_civil", "ingeniero_sistemas", "proveedor_tecnologia", "contador", "abogado", "psicologo", "medico", "docente", "disenador_grafico", "ingeniero_ambiental", "topografo", "electricista", "transportador", "enfermero", "nutricionista", "vigilancia_seguridad", "odontologo", "fisioterapeuta", "comunicador_social", "biologo", "publicista", "redes_equipos", "mantenimiento_locativo", "aseo_cafeteria", "suministro_alimentos", "eventos", "jardineria" ]
} as const;
