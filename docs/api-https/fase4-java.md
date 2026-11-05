# Acceso por HTTPS — Fase 4: implementación Api en Java

## Selección del modo

`config.properties`:

```properties
bd.modo=jdbc            # o api
api.url=https://dfsrvpplxueujiezdogi.supabase.co/functions/v1/api
```

`config.local.properties` (no se sube): `api.clave=sb_publishable_...` (o la variable `SECOP_API_CLAVE`).

`Servicios.conectar`:

| bd.modo | Qué pasa |
|---|---|
| `jdbc` | Igual que antes: Supabase por PostgreSQL y, si falla, H2 local con aviso. |
| `api` | Prueba `estado.datos`. Si responde, todo va por HTTPS. Si falta la clave, la clave es mala (403) o no hay conexión, **H2 local con aviso** en la pantalla de ingreso. |

`-Dsecop.bd.url=...` sigue forzando JDBC (lo usan las pruebas con copias de la base).

## Piezas

| Paquete / clase | Qué hace |
|---|---|
| `modelo.api.ClienteApi` | `java.net.http.HttpClient` + Jackson. POST `{url}/{operacion}`, cabeceras `apikey` y `x-secop-sesion`, errores → `ErrorApi` (estado, código, mensaje, campos). Un 401 borra el token. Subidas/descargas a Storage por URL firmada (sin la clave de la app). |
| `modelo.api.*Api` | Implementan los 9 repositorios. Lo que hace el servidor (ingesta, cruce, ajustar planes, verificar contraseñas, crear cuentas) lanza `SoloServidor`. |
| `ProcesoApi` | Guarda en memoria los procesos vigentes y luego pide solo los cambios (`desde`). Caché corta para `estado.datos` (30 s) e `inicio.datos` (5 min). |
| `CuentaServiceApi` | Login, registro, cambiar contraseña y nombre por la API. Las validaciones de la app (nombre, correo con DNS, fuerza de la contraseña) se mantienen antes de enviar. |
| `ServiciosApi.Perfiles` | Crear perfil = una llamada (`perfiles.crear`: límite + guardar + cruce en una transacción). |
| `ServiciosApi.Oportunidades` | Pide al servidor la base del panel (sin duplicados, ocultos ni exceso del plan) y aplica en la app los filtros y el orden de la pantalla. |
| `ServiciosApi.Documentos` | Subir = `pedirSubida` → PUT a Storage → `confirmarSubida` (el servidor revisa el archivo). |
| `ServiciosApi.Postulaciones` | Cada cambio de etapa = una acción `postulaciones.actualizar` validada en el servidor. |
| `ServiciosApi.Planes` | No ajusta nada en la app: lo hace el servidor. |
| `ServiciosApi.Actualizacion` | No descarga SECOP; "Actualizar ahora" llama `admin.actualizarAhora`. |

Cambios en el modo JDBC (sin cambiar su comportamiento):

- `OportunidadesService.panel` separa `base(...)` (lo que el plan deja ver) del armado del panel.
- `CuentaService.registrar` separa `validarRegistro(...)`; se agregó `cerrarSesion()` (vacío en JDBC).
- `Servicios.salir()`, `enLaNube()` y `descripcionDatos()`: los controladores ya no tocan `BaseDatos`.
- `LoginControlador` muestra el mensaje si el servidor bloquea por intentos o no hay conexión.
- Dependencia nueva: `jackson-datatype-jsr310` (fechas en el JSON).

En el servidor se agregó `oportunidades.ocultos` y la ventana de "vigentes" quedó igual que en Java
(cierre en los últimos 2 días incluido).

## Pruebas

- `RepositoriosApiTest` (10) y `ServiciosApiTest` (7) con `ServidorFalso`, un servidor HTTP en memoria
  (`com.sun.net.httpserver`) que imita la función y Storage. Sin red real.
- Cubren: cabeceras, errores y 401, caché e incremental de procesos, coincidencias, ocultos, historial
  solo de tablas conocidas, `SoloServidor`, descargas por URL firmada y base64, login/registro/logout,
  panel con cifras del servidor, subida en tres pasos (y que un archivo falso no sale del PC), acciones de
  postulación y la elección de modo con respaldo en H2.
- `mvnw.cmd test`: **77 pruebas, todas pasan**. La app en modo JDBC se probó de punta a punta sobre una
  copia de la base (postulación por etapas y ocultar oportunidades).

## Pendiente (fase 5)

- Desplegar la función y aplicar la migración (con tu permiso), y probar el modo API contra el servidor real.
- Uso mixto JDBC + API: un documento subido por la API queda en Storage; la app en modo JDBC no lo puede
  abrir (busca `contenido`). Lo recomendable es que todos usen el mismo modo, o migrar todo a API.
