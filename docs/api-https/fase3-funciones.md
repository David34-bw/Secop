# Acceso por HTTPS — Fase 3: Edge Function, sesiones y migración

Estado: **archivos locales, nada desplegado ni aplicado** en Supabase.

## Archivos

```
supabase/
  config.toml                          verify_jwt = false para "api" (la clave publishable no es un JWT)
  migrations/20261105210000_api_https.sql   sesiones, intentos_login, subidas_pendientes, columnas nuevas, bucket
  sql/programar-job.sql                pg_cron + pg_net + Vault (se corre a mano después de desplegar)
  functions/
    api/index.ts                       enrutador: POST /functions/v1/api/<operacion>
    api/deno.json                      postgres 3.4.5, bcryptjs 2.4.3, supabase-js 2.49.4
    api/lib/                           bd (SUPABASE_DB_URL), http, sesion, storage
    api/ops/                           cuenta, procesos, perfiles, documentos, postulaciones, admin, job
    _shared/dominio/                   lógica pura portada de Java (sin Deno ni npm)
      catalogo.generado.ts             GENERADO desde Java: no editar a mano
      pruebas/                         paridad con Java + reglas del servidor
```

## Seguridad

- La app solo conoce la URL y la clave **publishable**. La contraseña de la base va en
  `SUPABASE_DB_URL` (secreto que Supabase pone en la función) y la clave secreta en
  `SUPABASE_SECRET_KEYS`; ninguna sale del servidor.
- Ninguna operación recibe SQL ni nombres de tabla. Todas las consultas usan parámetros.
  Los dos `UPDATE` con columnas dinámicas arman los nombres desde listas fijas del código.
- Sesión: token aleatorio de 32 bytes en `x-secop-sesion`; en la base solo su SHA-256. Vence en 7 días.
  Cambiar la contraseña cierra las demás sesiones.
- El usuario siempre sale del token; las rutas `admin.*` revisan el rol en el servidor.
- Login: 10 fallos por correo o 30 por IP en 15 minutos → 429. Se compara bcrypt aunque el
  correo no exista (no delata qué correos están registrados).
- Registro en una transacción con `LOCK TABLE`: el "primer usuario es admin" ya no tiene carrera.
- El servidor vuelve a validar todo: nombre, contraseña, formato del correo, perfil, proponente,
  orden de las etapas, oferta ≤ presupuesto, cierre del proceso, tipo y tamaño de archivos
  (lee los primeros bytes del archivo subido).
- El límite de oportunidades del plan se aplica en el servidor (`oportunidades.vigentes`).
- Los errores internos responden un mensaje genérico; el detalle solo va al log de la función.

## Operaciones (44)

| Grupo | Operaciones |
|---|---|
| Sin sesión | `auth.login`, `auth.registrar`, `estado.datos` |
| Cuenta | `auth.logout`, `cuenta.yo`, `cuenta.cambiarNombre`, `cuenta.cambiarPassword`, `cuenta.avisos` |
| Procesos | `inicio.datos`, `procesos.vigentes` (con `desde` para pedir solo cambios), `procesos.porIds` |
| Oportunidades | `oportunidades.vigentes`, `oportunidades.marcarVistas`, `oportunidades.ocultar`, `oportunidades.mostrar` |
| Perfiles | `perfiles.listar`, `perfiles.crear` (límite + guardar + cruce en una transacción), `perfiles.eliminar` (+ reactivar por plan) |
| Documentos | `proponente.obtener`, `proponente.guardar`, `documentos.listar`, `documentos.pedirSubida`, `documentos.confirmarSubida`, `documentos.descargar`, `documentos.eliminar`, `documentos.cambiarVence` |
| Postulaciones | `postulaciones.listar`, `.idsPorProceso`, `.crear`, `.obtener`, `.actualizar` (acción: registro, revisar, pliego, notas, requisitoAgregar/Alternar/Quitar, propuesta, presentar, resultado, desistir), `.eliminar`, `.pedirSubidaPropuesta`, `.confirmarSubidaPropuesta`, `.descargarPropuesta` |
| Admin | `admin.resumen`, `admin.usuarios`, `admin.historial` (solo `ingesta` o `coincidencias`), `admin.asignarPlan`, `admin.asignarRol`, `admin.reiniciarPrueba`, `admin.actualizarAhora` |
| Job (secreto) | `job.ingesta` → `job.cruce` → `job.planes` |

Los JSON usan los mismos nombres que los records de Java (`Usuario`, `Perfil`, `Proceso`,
`Documento`, `Postulacion`, `Proponente`) para que la fase 4 los lea directo con Jackson.

## Actualización diaria en el servidor (decisión: opción B)

`pg_cron` llama `job.ingesta` a las 21:00 de Colombia. Cada paso hace un lote y se vuelve a
llamar para el siguiente, para no pasar los **2 s de CPU por petición** de las Edge Functions:

1. `job.ingesta`: una página de 1000 procesos de datos.gov.co y un solo `INSERT … ON CONFLICT`.
2. `job.cruce`: 10 perfiles por llamada contra todos los procesos vigentes.
3. `job.planes`: gracias vencidas, perfiles según el plan y limpieza (sesiones, intentos, subidas).

Cada paso escribe en `ingesta_log` / `coincidencias_log`, que la pantalla de Administración ya muestra.

## Documentos en Storage (decisión aprobada)

`pedirSubida` (valida tipo, tamaño y vencimiento; devuelve URL firmada) → la app hace `PUT`
del archivo directo a Storage por HTTPS → `confirmarSubida` (descarga el archivo, revisa tamaño
y primeros bytes, y lo registra en una transacción; si reemplaza uno, borra el anterior).
Los archivos que ya están en la base (`contenido`) se siguen pudiendo descargar (`base64`).

## Pruebas

```
node --test supabase/functions/_shared/dominio/pruebas/paridad.test.ts supabase/functions/_shared/dominio/pruebas/reglas.test.ts
```

- **Paridad con Java** (casos generados por la app Java): Matching con 30 perfiles × 1200
  procesos (1080 puntajes idénticos), nombres, contraseñas, correos, plan efectivo, fechas de
  SECOP, proponente y grupo de modalidad.
- **Reglas del servidor**: etapas, oportunidades visibles, perfil y ajuste de planes.
- Total: 14 pruebas, todas pasan. `mvnw.cmd test` sigue en verde (60).

## Lo que NO está verificado todavía

- No hay Deno en este computador: el código de la función está revisado en sintaxis
  (todos los archivos compilan quitando los tipos) pero **no** con `deno check` ni ejecutado.
  La guía de la fase 5 incluye `supabase functions serve` para probarla en local antes de desplegar.
- Uso mixto: si alguien sube un documento por la API (queda en Storage) y otro lo abre con la
  app en modo JDBC, la app JDBC no lo encuentra (busca `contenido`). Se resuelve en la fase 4.
- Las 4 migraciones que ya están en Supabase (aplicadas desde aquí) no están en
  `supabase/migrations/`; la guía explica `supabase migration fetch` para traerlas antes de usar la CLI.
