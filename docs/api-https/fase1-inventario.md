# Acceso por HTTPS — Fase 1: inventario y propuesta de endpoints

Estado: **propuesta para revisar**. No hay código todavía.

## 1. Lo que encontré y que cambia el diseño

1. **Cada computador hace trabajo de servidor.** `CronService` corre en todas las
   apps: descarga SECOP (`IngestaService`), cruza **todos** los perfiles de **todos**
   los usuarios (`CoincidenciasService.ejecutar`) y ajusta los planes de todos
   (`PlanesService.ajustarTodos`). En modo API eso no se puede hacer desde el
   cliente sin endpoints de "leer todo / escribir todo". Propuesta: en modo `api`
   el cliente **nunca** ingesta ni cruza; lo hace el servidor (ver §4).
2. **Hoy la contraseña de `postgres` está en cada PC** (modo JDBC). Cualquiera
   con el `config.local.properties` puede leer y borrar todo. El modo API arregla esto.
3. **`schema.sql` se ejecuta desde el cliente** al conectar. En Supabase crea las
   tablas que falten **sin RLS** (las de `secop` tienen RLS porque las creé con
   migraciones). En modo API el cliente no ejecuta DDL.
4. **`CoincidenciaDAO.marcarNotificadas(ids)` no filtra por usuario**: con ids
   ajenos marcaría coincidencias de otro. En la API se filtra por el usuario del token.
5. **`LimiteIntentos` (bloqueo de login) vive en memoria del cliente**: reiniciar la
   app lo salta. En la API va en el servidor.
6. **Carrera en `registrar`**: "si no hay usuarios, el primero es admin" son dos
   sentencias; dos registros a la vez pueden crear dos admins. Debe ir en una transacción.
7. **Buscar y la vista previa de perfiles traen todos los procesos vigentes** al
   cliente (`ProcesoDAO.vigentes()`, miles de filas con el objeto completo) y hacen el
   *matching* en Java. Ver §3 (endpoint `procesos.vigentes` compacto y en caché).

## 2. Inventario de SQL por DAO

Leyenda de alcance: **U** = filas del usuario de la sesión · **G** = global/todos ·
**A** = solo admin · **S** = solo servidor (job).

### UsuarioDAO (`users`)
| Método | SQL | Quién lo usa | Alcance |
|---|---|---|---|
| porId | SELECT columnas WHERE id=? | casi todos los controladores, PlanesService | U (A para otros) |
| porEmail | SELECT … WHERE email=? | CuentaService.registrar/autenticar | servidor (login) |
| hashDe | SELECT password_hash WHERE email=? | CuentaService.autenticar | **solo servidor** |
| todos | SELECT … ORDER BY id | Admin, PlanesService.ajustarTodos | A / S |
| contar | SELECT count(*) | registrar (primer admin), Admin | S / A |
| crear | INSERT users (…) | registrar | servidor |
| cambiarNombre / cambiarPassword / cambiarAvisos | UPDATE … WHERE id=? | Mi cuenta | U |
| asignarPlan / asignarRol / reiniciarPrueba | UPDATE … WHERE id=? | Admin | A |
| vencerGracias | UPDATE … WHERE gracia_hasta < ? | PlanesService.ajustarTodos | S |

### PerfilDAO (`perfiles`)
| Método | SQL | Quién | Alcance |
|---|---|---|---|
| deUsuario / deUsuarioPorAntiguedad | SELECT * WHERE user_id=? | Mis búsquedas, planes | U |
| activos | SELECT * WHERE activo | cruce global | S |
| porId | SELECT * WHERE id=? | PerfilesService.crear | U |
| contarActivos / contar | count | límite del plan / Admin | U / A |
| crear | INSERT perfiles | PerfilesService.crear | U |
| eliminar | DELETE WHERE id=? AND user_id=? | Mis búsquedas | U |
| cambiarEstado | UPDATE activo, desactivado_por_plan | PlanesService | U (vía plan) / S |
| rankingProfesiones | SELECT profesion_id, count(*) GROUP BY | Admin | A |

### ProcesoDAO (`procesos`)
| Método | SQL | Quién | Alcance |
|---|---|---|---|
| vigentes | SELECT columnas WHERE cierre ≥ ahora | Buscar, cruce, vista previa | G (solo lectura) |
| porIds | SELECT … WHERE id IN (…) | Postulaciones | G (lectura) |
| existentes / insertar (lote) / actualizar (lote) | SELECT ids, INSERT/UPDATE masivos | IngestaService | S |
| contar / ultimoIngreso | count, max(ingresado_at) | barra de estado, Admin | G |
| abiertosPorDepartamentoYTipo / resumenAbiertos / ofertasMinimaCuantia | agregados | Inicio | G |

### CoincidenciaDAO (`coincidencias`)
| Método | SQL | Quién | Alcance |
|---|---|---|---|
| vigentesDeUsuario | JOIN coincidencias–perfiles–procesos WHERE pf.user_id=? | Oportunidades, alertas | U |
| marcarNotificadas | UPDATE notificado WHERE id IN (…) **(sin user_id)** | Oportunidades | U (corregir) |
| dePerfil / insertar / actualizarScores / eliminar | por perfil | cruce, perfil nuevo | S / U (perfil nuevo) |
| contar | count | Admin | A |

### LogDAO (`ingesta_log`, `coincidencias_log`)
| ingesta / coincidencias | INSERT | Ingesta, cruce | S |
|---|---|---|---|
| ultimas(tabla, n) | SELECT * FROM **tabla concatenada** | Admin | A (en la API: lista cerrada de tablas) |

### ProponenteDAO (`proponentes`)
| deUsuario | SELECT WHERE user_id=? | Mis documentos | U |
|---|---|---|---|
| guardar | UPDATE y, si 0 filas, INSERT (**2 sentencias**) | Mis documentos, postulaciones | U → `INSERT … ON CONFLICT` |

### DocumentoDAO (`documentos`)
| deUsuario / porId / contenido | SELECT (contenido = bytea) | Mis documentos | U |
|---|---|---|---|
| crear + eliminarTipo | DELETE tipo + INSERT (**2 sentencias** al reemplazar) | subir | U → una transacción |
| eliminar / cambiarVence | DELETE / UPDATE WHERE id=? AND user_id=? | Mis documentos | U |

### PostulacionDAO (`postulaciones`)
| deUsuario / porId / idsPorProceso | SELECT WHERE user_id=? | Mis postulaciones, tarjetas | U |
|---|---|---|---|
| crear / refrescar | INSERT / UPDATE desde `procesos` | postularse / abrir | U |
| marcarRevisado, marcarPliego, guardarNotas, guardarRequisitos, guardarPropuesta, guardarArchivoPropuesta, marcarPresentada, guardarResultado, marcarDesistida, eliminar | UPDATE/DELETE WHERE id=? AND user_id=? | etapas | U |
| archivoPropuesta | SELECT bytea | abrir propuesta | U |

**Regla de negocio que hoy está solo en Java y debe validarse en el servidor:** el orden
de las etapas (`Avance` + `PostulacionesService.exigir/exigirSinPosteriores`) y el
tope "oferta ≤ presupuesto".

### Operaciones de varias sentencias (deben ser una sola función / transacción)
| Operación | Hoy | En la API |
|---|---|---|
| registrar | porEmail + contar + crear | `auth.registrar` en una transacción (primer usuario = admin) |
| crear perfil | contarActivos + crear + cruce del perfil (vigentes + matching + INSERT lote) | `perfiles.crear` |
| sincronizar perfiles con el plan | N × cambiarEstado | dentro de `admin.asignarPlan` y del job |
| subir documento único | eliminarTipo + crear | `documentos.confirmarSubida` |
| guardar proponente | UPDATE + INSERT | upsert |
| cruce global | por perfil: SELECT + INSERT + UPDATE + DELETE | job del servidor |

## 3. Endpoints propuestos

**Recomendación:** una sola Edge Function `api` con un enrutador interno
(`POST /functions/v1/api/<operacion>`), en vez de ~40 funciones: un despliegue,
código compartido (validación de token, conexión, errores) y menos arranques en frío.
Cada operación sigue siendo de dominio, con su propio validador de entrada; no existe
ninguna que reciba SQL ni nombres de tabla.

Cabeceras: `apikey: <publishable>` (la app) y `x-secop-sesion: <token>` (el usuario).
No se usa `Authorization: Bearer` con la clave publishable porque la plataforma
intenta leerla como JWT (ver §6).

### Sin sesión
| Operación | Entrada | Salida |
|---|---|---|
| auth.login | email, password | token, expira, usuario (sin hash) · límite de intentos en servidor |
| auth.registrar | email, password, nombre, aceptaTerminos | token, usuario · valida nombre/contraseña también en servidor |
| estado.datos | — | procesos, ultimoIngreso (pantalla de ingreso y barra de estado) |

### Con sesión (usuario sale del token, nunca del cuerpo)
| Operación | Entrada | Salida |
|---|---|---|
| auth.logout | — | ok (borra la sesión) |
| cuenta.yo | — | usuario + datos del plan |
| cuenta.cambiarNombre / cuenta.cambiarPassword / cuenta.avisos | nombre / actual+nueva / activos | usuario |
| inicio.datos | — | lo de PortadaService |
| procesos.vigentes | `desde` (opcional, para pedir solo cambios) | lista compacta para Buscar y la vista previa (el *matching* sigue en Java) |
| oportunidades.vigentes | — | filas de `vigentesDeUsuario` (filtros, duplicados y plan siguen en Java) |
| oportunidades.marcarVistas | ids | ok (filtrado por usuario) |
| oportunidades.ocultar / oportunidades.mostrar | procesoId | ok (para la tarea del menú contextual) |
| perfiles.listar / perfiles.eliminar | — / id | perfiles |
| perfiles.crear | borrador | id, coincidencias encontradas (límite del plan validado en servidor) |
| proponente.obtener / proponente.guardar | — / datos | proponente |
| documentos.listar / eliminar / cambiarVence | — / id / id+fecha | documentos |
| documentos.pedirSubida | tipo, nombre, tamaño, vence | URL firmada de subida (ver §5) |
| documentos.confirmarSubida | id de subida | documento (reemplaza el anterior en una transacción) |
| documentos.descargar | id | URL firmada de descarga (corta duración) |
| postulaciones.listar / crear / obtener / eliminar | — / procesoId / id / id | postulación + avance |
| postulaciones.actualizar | id, `accion` (revisar, pliego, notas, requisito+/-/✓, propuesta, presentar, resultado, desistir) + datos | postulación + avance · **valida el orden de etapas en servidor** |

### Solo admin (rol verificado en servidor)
| admin.resumen, admin.usuarios, admin.historial(tipo ∈ {ingesta, coincidencias}) | — | estadísticas y listas |
|---|---|---|
| admin.asignarPlan / admin.asignarRol / admin.reiniciarPrueba | usuarioId + valor | usuario (sincroniza perfiles en la misma transacción) |
| admin.actualizarAhora | — | encola el job de ingesta |

### Solo servidor
| job.ingesta | llamado por `pg_cron` + `pg_net` con un secreto propio | descarga SECOP, cruza perfiles, ajusta planes |
|---|---|---|

## 4. Ingesta diaria: ¿lotes por API o en el servidor?

| | A. La app de un admin envía lotes por API | B. Job programado en el servidor (recomendado) |
|---|---|---|
| Código | Reutiliza todo el Java (Socrata, mapeo, Matching) | Hay que portar a TypeScript la descarga, el mapeo y el **Matching** (mantener paridad con pruebas compartidas) |
| Depende de | Que alguien tenga la app abierta a las 21:00 | Nada; corre solo |
| Seguridad | Endpoint de escritura masiva (aunque solo admin) | Sin escritura masiva expuesta |
| Límites | Lotes chicos por los 2 s de CPU por petición | Igual: hay que paginar (p. ej. 500–1000 procesos por invocación y encadenar), wall clock 150 s en plan Free |
| Varios clientes | Hoy cada app ingesta y cruza: corre N veces | Una sola vez |

El modo JDBC/H2 seguiría usando la ingesta en Java tal como está.

## 5. Documentos de hasta 5 MB

- Límites documentados de Edge Functions: 256 MB de memoria, **2 s de CPU por petición**,
  150 s de inactividad. **No encontré en la documentación un límite explícito de tamaño
  del cuerpo**; no lo asumo.
- Mandar 5 MB en JSON (base64 ≈ 6,7 MB) y decodificarlos dentro de la función gasta CPU
  y memoria, y guarda binarios en la base (el plan Free tiene 500 MB de base).
- **Recomendación: Supabase Storage**, bucket privado `documentos`:
  `documentos.pedirSubida` valida tipo/tamaño/usuario y devuelve una URL firmada; la app
  sube el archivo directo a Storage por HTTPS (443); `confirmarSubida` revisa que el archivo
  exista, lee los primeros bytes (PDF/JPG/PNG) y guarda los metadatos. Plan Free: hasta 50 MB por archivo.
- En modo JDBC/H2 se mantiene `bytea`. Habría que migrar los archivos existentes de
  `documentos.contenido` y `postulaciones.propuesta_contenido` a Storage (script aparte).

## 6. Datos verificados en la documentación de Supabase

- Secreto para conectarse a Postgres desde una Edge Function: **`SUPABASE_DB_URL`**
  (variable por defecto). Fuente: guía *Environment variables* de Edge Functions.
- Las claves nuevas `sb_publishable_…` **no son JWT**: hay que desplegar con
  `verify_jwt = false` y validar en el código. No se deben mandar en `Authorization: Bearer`.
- Nombres de secretos propios: no pueden empezar por `SUPABASE_`.

## 7. Preguntas para aprobar la fase 1

1. ¿Una función `api` con enrutador (recomendado) o una función por operación?
2. Ingesta: ¿opción B (servidor, portar Matching a TypeScript)?
3. Documentos: ¿pasar a Storage en modo API?
4. ¿Corrijo ya en el modo JDBC el filtro por usuario de `marcarNotificadas` (punto 1.4)?

## 8. Decisiones (aprobadas el 5 de noviembre de 2026)

1. Una sola Edge Function `api` con enrutador interno.
2. Ingesta: **opción B**, en el servidor (job programado), portando el Matching a TypeScript.
3. Documentos: a **Supabase Storage** en modo API.
4. `marcarNotificadas` ya filtra por usuario en el modo JDBC (`OportunidadesService.marcarVistas`).

## 9. Fase 2 (hecha)

- Interfaces en `co.secopradar.modelo.repositorio`: `UsuarioRepositorio`, `PerfilRepositorio`,
  `ProcesoRepositorio`, `CoincidenciaRepositorio`, `LogRepositorio`, `ProponenteRepositorio`,
  `DocumentoRepositorio`, `PostulacionRepositorio`, `ProcesoOcultoRepositorio`.
- Los DAO de `co.secopradar.modelo.dao` las implementan (JDBC, sin cambios de comportamiento).
- Los servicios y `Servicios` dependen de las interfaces; solo `Servicios` sabe qué implementación usa.
- Los records `ProcesoRepositorio.Fila` y `CoincidenciaRepositorio.Fila` pasaron a las interfaces.
