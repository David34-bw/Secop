# Acceso por HTTPS — Fase 5: guía de despliegue

> **Estado (2026-11-05):** ya está todo desplegado con `scripts/configurar-api.ps1` y verificado (función
> `api` activa, migración aplicada, bucket `documentos`, job diario programado y primera actualización sin
> errores). Esta guía sirve para repetirlo o para entender cada paso.

Esta guía la ejecutas tú, paso a paso.
Todos los comandos son para **PowerShell** (la terminal de IntelliJ o de Windows), desde la carpeta
del proyecto (`C:\Users\david\Desktop\Secop`).

> Si prefieres que yo haga algún paso con el conector de Supabase (aplicar la migración, revisar
> tablas, programar el job), pídemelo y te pregunto antes de cada acción.

## Opción rápida: el script

`scripts/configurar-api.ps1` hace los pasos 1 a 9 solo. Ejecútalo **en tu PC de la casa** (necesita el
puerto 5432 de PostgreSQL, que la universidad bloquea) y donde esté `config.local.properties` con `bd.password`:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\configurar-api.ps1
```

- Para ver qué haría sin tocar nada: agrega `-Simular`.
- La contraseña se lee de `config.local.properties`; no se muestra ni se escribe en otro lado.
- El secreto del job se genera, va a la función (por un archivo temporal que se borra) y a Vault (por la
  herramienta `co.secopradar.herramientas.ConfigurarServidor`), y nunca se muestra.
- Lo único manual: si la CLI no tiene sesión, se abre el navegador para autorizarla.
- Al final cambia `bd.modo=api` en `config.properties`. Haz commit y push; en la universidad basta con
  `git pull` y ejecutar la app (no necesita `config.local.properties`).
- Se puede repetir sin duplicar nada.

Los pasos de abajo son lo mismo, a mano.

## Antes de empezar

1. **Haz commit** de las fases 1 a 4 (para poder volver atrás si algo sale mal).
2. Ten a mano la **clave publishable** del proyecto: supabase.com → Project_Two → **Settings › API Keys**
   → la que empieza por `sb_publishable_`. **No** uses la `sb_secret_`: esa nunca va en la app.
3. No necesitas Docker: el despliegue se hace con `--use-api`.

## Paso 1. Supabase CLI

Con Node (ya lo tienes) no hay que instalar nada; `npx` la descarga la primera vez. La guía usa la
versión 3 de la CLI: ahí `migration` y `db push` necesitan `--remote` para trabajar sobre Supabase (sin
`--remote` buscan una base local con Docker).

```powershell
npx supabase --version
```

Si te pregunta `Need to install the following packages: supabase... Ok to proceed?`, responde `y`.

## Paso 2. Iniciar sesión y enlazar el proyecto

```powershell
npx supabase login
npx supabase link --project-ref dfsrvpplxueujiezdogi
```

`login` abre el navegador. `link` puede pedir la contraseña de la base: es la misma de `bd.password`
en tu `config.local.properties`. La CLI la guarda en `supabase/.temp/`, que ya está en el `.gitignore`.

## Paso 3. Traer el historial de migraciones

Las tablas que ya existen las creé con el conector y Supabase guardó ese historial. Tráelo para que la
CLI no intente crearlas otra vez:

```powershell
npx supabase migration fetch --remote
npx supabase migration list --remote
```

En `migration list` deben aparecer 4 migraciones en **Local y Remote** y una sola solo en **Local**:
`20261105210000` (la nueva, `api_https`).

## Paso 4. Aplicar la migración nueva

```powershell
npx supabase db push --remote
```

Te muestra la migración `20261105210000_api_https.sql` y pide confirmar. Crea las tablas `sesiones`,
`intentos_login` y `subidas_pendientes`, agrega las columnas `documentos.ruta`,
`postulaciones.propuesta_ruta` y `procesos.actualizado_at`, y crea el bucket privado `documentos`.

Comprueba en **Table Editor → schema secop** que estén las tablas nuevas y en **Storage** el bucket
`documentos` (privado).

## Paso 5. Secretos de la función

Genera un secreto aleatorio para el job diario (solo lo usan `pg_cron` y la propia función):

```powershell
$b = New-Object byte[] 32; [System.Security.Cryptography.RNGCryptoServiceProvider]::new().GetBytes($b); $secreto = [Convert]::ToBase64String($b) -replace '[+/=]', ''
```

```powershell
npx supabase secrets set SECOP_JOB_SECRET=$secreto
```

No cierres esta terminal: `$secreto` se usa en los pasos 7 y 8. No lo guardes en ningún archivo del proyecto.

Opcional: si tienes un token de datos.gov.co (evita límites de la API de Socrata):

```powershell
npx supabase secrets set SOCRATA_APP_TOKEN=tu_token
```

`SUPABASE_DB_URL`, `SUPABASE_URL` y las claves del proyecto ya las pone Supabase solo; no se configuran.

## Paso 6. Desplegar la función

```powershell
npx supabase functions deploy api --use-api
```

En el Dashboard → **Edge Functions → api**, revisa que **Verify JWT** esté **apagado** (lo dice
`supabase/config.toml`; la función valida la clave y la sesión en su propio código).

## Paso 7. Probar la función

```powershell
$clave = "sb_publishable_PEGA_AQUI_TU_CLAVE"
$api = "https://dfsrvpplxueujiezdogi.supabase.co/functions/v1/api"
Invoke-RestMethod -Method Post -Uri "$api/estado.datos" -Headers @{ apikey = $clave } -ContentType "application/json" -Body "{}"
```

Debe responder algo como `procesos : 2637` y `ultimoIngreso : 2026-...`.

Pruebas de seguridad (deben **fallar**):

```powershell
Invoke-RestMethod -Method Post -Uri "$api/estado.datos" -ContentType "application/json" -Body "{}"
```

→ 403 "Falta la clave de la aplicación".

```powershell
Invoke-RestMethod -Method Post -Uri "$api/cuenta.yo" -Headers @{ apikey = $clave } -ContentType "application/json" -Body "{}"
```

→ 401 "Inicia sesión de nuevo".

```powershell
Invoke-RestMethod -Method Post -Uri "$api/job.planes" -ContentType "application/json" -Body "{}"
```

→ 403 (sin el secreto del job).

Primera actualización a mano (descarga SECOP, cruza perfiles y ajusta planes, en cadena):

```powershell
Invoke-RestMethod -Method Post -Uri "$api/job.ingesta" -Headers @{ "x-secop-job" = $secreto } -ContentType "application/json" -Body '{"offset":0,"dias":5}'
```

Responde enseguida con el primer lote; el resto sigue solo. Después de 1 o 2 minutos revisa en el
**SQL Editor**:

```sql
SELECT * FROM secop.ingesta_log ORDER BY id DESC LIMIT 3;
SELECT * FROM secop.coincidencias_log ORDER BY id DESC LIMIT 3;
```

Deben aparecer filas nuevas con `exito = true`. Si hay error, el detalle está en
**Edge Functions → api → Logs**.

## Paso 8. Programar la actualización diaria

1. Abre `supabase/sql/programar-job.sql`.
2. **Copia** su contenido al **SQL Editor** del Dashboard (no edites el archivo del proyecto).
3. En el SQL Editor, reemplaza `PEGA_AQUI_EL_MISMO_VALOR_DE_SECOP_JOB_SECRET` por el valor de `$secreto`
   (escribe `$secreto` en la terminal para verlo).
4. Ejecuta. Activa `pg_cron` y `pg_net`, guarda la URL y el secreto en Vault (cifrados) y programa el job
   a las 21:00 de Colombia.

Comprueba:

```sql
SELECT jobname, schedule, active FROM cron.job;
```

Al día siguiente:

```sql
SELECT status, return_message, start_time FROM cron.job_run_details ORDER BY start_time DESC LIMIT 5;
```

## Paso 9. Configurar la app

En `config.local.properties` (no se sube a GitHub) agrega:

```properties
bd.modo=api
api.clave=sb_publishable_PEGA_AQUI_TU_CLAVE
```

Poner `bd.modo=api` en `config.local.properties` lo cambia solo en ese computador. Si quieres que sea
el modo de todos, cámbialo en `src/main/resources/config.properties`.

Ejecuta `Lanzador`. En la pantalla de ingreso debe decir
**"Datos en: Supabase por HTTPS (dfsrvpplxueujiezdogi.supabase.co)"**.

Prueba en la app:

- [ ] Iniciar sesión con tu cuenta (y una vez con una contraseña mala).
- [ ] Oportunidades: ver la lista, clic derecho → Ocultar y "Mostrar ocultas".
- [ ] Mis búsquedas: crear un perfil y borrarlo.
- [ ] Mis documentos: guardar datos, subir un PDF, abrirlo y quitarlo.
- [ ] Mis postulaciones: postularte y avanzar algunas etapas.
- [ ] Administración: historial y "Actualizar ahora".

En la universidad solo necesitas `bd.modo=api` y `api.clave`: la contraseña de la base no hace falta.

## Volver atrás

- App: `bd.modo=jdbc` (o quita la línea de `config.local.properties`).
- Job: `SELECT cron.unschedule('secop-actualizacion-diaria');`
- Función: `npx supabase functions delete api`
- Las tablas nuevas no estorban al modo JDBC; no hace falta borrarlas.

## Problemas comunes

| Síntoma | Causa y solución |
|---|---|
| 403 "Falta la clave de la aplicación" | `api.clave` está vacía o no es la publishable del proyecto. |
| `Invalid JWT` | Verify JWT quedó encendido: vuelve a desplegar con `npx supabase functions deploy api --use-api --no-verify-jwt`. |
| 401 al usar la app | La sesión venció (7 días) o cambiaste la contraseña en otro PC: inicia sesión de nuevo. |
| 404 "Operación desconocida" | La función desplegada es una versión vieja: repite el paso 6. |
| 500 | Mira **Edge Functions → api → Logs**; la app solo muestra un mensaje genérico. |
| El job no corre | `SELECT * FROM net._http_response ORDER BY created DESC LIMIT 5;` y revisa que el secreto de Vault sea igual a `SECOP_JOB_SECRET`. |
| La app abre "en base local" con aviso | Lee el aviso: sin clave, clave mala o sin conexión con el servidor. |

## Seguridad: después de que funcione

1. **Usa un solo modo en todos los computadores.** En modo JDBC cada PC sigue descargando SECOP y
   cruzando perfiles por su cuenta. Además, un documento subido por la API (queda en Storage) no se
   puede abrir desde un PC en modo JDBC.
2. Cuando todos estén en modo API, **cambia la contraseña de la base** (Settings › Database › Reset
   database password). Así deja de servir la contraseña que quedó en los `config.local.properties`
   viejos. Actualízala en el paso 2 (`link`) si vuelves a usar la CLI.
3. Revisa los avisos de seguridad: Dashboard → **Advisors → Security**. Los avisos "RLS enabled no
   policy" de `secop` son intencionales: nadie entra con las claves públicas, solo la función.
4. Nunca subas a GitHub `config.local.properties`, `supabase/.temp/` ni el valor de `SECOP_JOB_SECRET`.
