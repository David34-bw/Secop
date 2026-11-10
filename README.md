# SECOP RADAR — aplicación de escritorio (JavaFX)

Versión de escritorio en **Java 21 + JavaFX (FXML)** del proyecto
[Julianskuu/Secop-radar](https://github.com/Julianskuu/Secop-radar), que originalmente era una web en Next.js.
Descarga los procesos de contratación de **SECOP II** desde datos.gov.co, los cruza con los perfiles de búsqueda
de cada usuario y muestra las oportunidades ordenadas por puntaje.

La aplicación es de escritorio: no hay que abrir ningún navegador. Los datos se guardan en **Supabase**.
Por defecto (`bd.modo=api`) la app llega a ellos **por HTTPS** con la Edge Function `api`, así que funciona
en cualquier computador (también en redes con permisos limitados, como la de la universidad) sin configurar
nada: solo `git clone` y ejecutar. Si no hay internet, usa una base local H2 (`./data/secop.mv.db`) y lo avisa.

---

## Modo JDBC: conectar directo a PostgreSQL (opcional)

Solo hace falta si cambias a `bd.modo=jdbc`. La contraseña de la base **no está en el código** (no se sube a GitHub):

1. Copia `config.local.properties.ejemplo` como **`config.local.properties`** (en la carpeta del proyecto,
   junto al `pom.xml`).
2. Escribe la contraseña de la base de Supabase en `bd.password=`. Está en supabase.com → proyecto
   **Project_Two** → **Project Settings › Database** (si no la recuerdas: *Reset database password*).
3. Ejecuta la app: en la pantalla de ingreso dice **"Datos en: Supabase (PostgreSQL en la nube)"**.

Las tablas están en el esquema **`secop`** del proyecto (no en `public`), con RLS activo: no se pueden leer
con las claves públicas de Supabase, solo desde la app. La primera vez que la app abre con la base vacía
descarga los procesos de SECOP II de los últimos 7 días.

### Modo API por HTTPS (el que está activo)

En algunas redes (por ejemplo, la universidad) el firewall corta el puerto 5432 de PostgreSQL. Por eso
`config.properties` tiene `bd.modo=api`: la app habla por HTTPS (puerto 443) con la Edge Function `api`
de Supabase y **no necesita la contraseña de la base**, solo la clave *publishable*, que ya está en
`config.properties` (es pública por diseño). En la pantalla de ingreso dice
**"Datos en: Supabase por HTTPS (dfsrvpplxueujiezdogi.supabase.co)"**.

- La actualización diaria de SECOP la hace el servidor a las 21:00 de Colombia (`pg_cron`), no cada computador.
- Los documentos subidos se guardan en Supabase Storage (bucket privado `documentos`).
- La sesión dura 7 días; después hay que volver a ingresar.
- **Rápida al cambiar de pestaña:** las respuestas del servidor quedan en memoria (`MemoriaApi`) y, al
  ingresar, la app pide en segundo plano lo que usan las pantallas. Guardar, borrar u ocultar algo vacía la
  memoria del usuario; cerrar sesión la vacía toda; lo que cambia el job diario dura 5 minutos. El botón
  **⟳ Actualizar datos** (abajo a la derecha) la vacía a mano.
- Si la API no responde, la app usa la base local y lo avisa.

El servidor **ya está configurado**. Si alguna vez hay que repetirlo (por ejemplo, tras cambiar el código de
`supabase/functions/api`), se ejecuta una vez desde un PC que sí llegue a PostgreSQL y tenga
`config.local.properties` con la contraseña:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\configurar-api.ps1
```

El código del servidor está en `supabase/` y la guía completa en `docs/api-https/`.

## Cómo correrlo en IntelliJ IDEA

1. **Abrir el proyecto**: `File > Open...` → carpeta del proyecto (la que tiene `pom.xml`) → **Trust Project**.
   IntelliJ detecta Maven y descarga las dependencias (JavaFX, H2, Jackson, jBCrypt).
   Si ya lo tenías abierto de antes, recarga Maven: panel **Maven** (derecha) → botón 🔄 *Reload All Maven Projects*.
2. **JDK 21**: `File > Project Structure > Project > SDK` → **21** (por ejemplo `temurin-21`).
3. **Ejecutar**: abre `src/main/java/co/secopradar/Lanzador.java` y presiona el ▶ verde junto a `main`.

> Ejecuta **`Lanzador`**, no `App`. `Lanzador` existe para que JavaFX cargue sin configurar el *module-path* a mano.

La primera vez, si la base está vacía, la app descarga en segundo plano los procesos de los últimos 7 días
(la barra de abajo muestra el avance). **La primera cuenta que se registra queda como administradora.**

Desde la terminal también funciona:

```bash
mvnw.cmd javafx:run
```

Tests (94: reglas portadas de la web original, ocultar procesos y foto con H2, el modo API y su memoria contra un servidor falso):

```bash
mvnw.cmd test
```

---

## Estructura Modelo-Vista-Controlador

```
src/main/java/co/secopradar/
├── Lanzador.java, App.java        ← arranque de JavaFX
├── modelo/                        ← MODELO: datos y reglas, sin nada de interfaz
│   ├── Usuario, Perfil, Proceso, Plan, Oportunidad, Region, RangoValor, ...   (entidades)
│   ├── Proponente, Documento, TipoDocumento, Preparacion                      (Mis documentos)
│   ├── Postulacion, Etapa, Avance                                             (Mis postulaciones)
│   ├── repositorio/ interfaces de acceso a datos (UsuarioRepositorio, ProcesoRepositorio, ...)
│   ├── dao/        modo JDBC: BaseDatos (JDBC + schema.sql), UsuarioDAO, PerfilDAO, ProcesoDAO,
│   │               CoincidenciaDAO, LogDAO, ProponenteDAO, DocumentoDAO, PostulacionDAO, ProcesoOcultoDAO
│   ├── api/        modo API: ClienteApi (HTTPS + JSON) y los repositorios que llaman a la función `api`
│   ├── servicio/   Servicios (elige el modo y arma todo), CuentaService, PerfilesService, IngestaService,
│   │               CoincidenciasService + Matching (puntaje), OportunidadesService, BusquedaService,
│   │               PortadaService, PlanesService, CronService, DocumentosService, PostulacionesService,
│   │               LegalService; CuentaServiceApi y ServiciosApi (variantes del modo API)
│   └── util/       Texto, TextoOracion, Formato, Registro
├── controlador/                   ← CONTROLADOR: un controlador por vista FXML
│   ├── Navegador, Pantalla, Vistas (carga las vistas y cambia de pantalla)
│   ├── LoginControlador, RegistroControlador, PrincipalControlador
│   ├── InicioControlador, OportunidadesControlador, TarjetaOportunidadControlador (con menú de clic derecho)
│   ├── PerfilesControlador, TarjetaPerfilControlador, NuevoPerfilControlador, GrupoOpcionesControlador
│   ├── DocumentosControlador, PostulacionesControlador, PostulacionControlador
│   └── BuscarControlador, PlanesControlador, CuentaControlador, AdminControlador, LegalControlador
└── herramientas/ConfigurarServidor ← solo para el script de configuración (no es parte de la app)

src/main/resources/
├── co/secopradar/vista/           ← VISTA: archivos FXML + estilos.css + imágenes
│   ├── login.fxml, registro.fxml, principal.fxml
│   ├── inicio.fxml, oportunidades.fxml, tarjetaOportunidad.fxml
│   ├── perfiles.fxml, tarjetaPerfil.fxml, nuevoPerfil.fxml, grupoOpciones.fxml
│   ├── documentos.fxml, postulaciones.fxml, postulacion.fxml
│   └── buscar.fxml, planes.fxml, cuenta.fxml, admin.fxml, legal.fxml
├── config.properties              ← configuración (modo de datos, clave publishable, hora de actualización...)
├── schema.sql                     ← tablas de la base (modo JDBC y H2)
├── datos/                         ← catálogos: profesiones y áreas UNSPSC
└── legal/                         ← términos y política de datos

supabase/                          ← servidor del modo API
├── functions/api/                 ← Edge Function (TypeScript/Deno): una función con enrutador
├── functions/_shared/dominio/     ← reglas (puntaje, planes, validaciones) iguales a las de Java, con pruebas
├── migrations/                    ← tablas del esquema `secop`
└── sql/programar-job.sql          ← actualización diaria con pg_cron

scripts/configurar-api.ps1         ← configura todo el servidor (se ejecuta una vez)
docs/api-https/                    ← diseño y guía de despliegue del modo API
```

- Las vistas (`.fxml`) solo describen la pantalla; se pueden abrir y editar en **Scene Builder**.
- Los controladores responden a los botones y pasan los datos al modelo; no tienen SQL ni reglas de negocio.
- El modelo no sabe nada de JavaFX: valida, calcula y guarda.

## Pantallas

| Pantalla | Qué hace |
|---|---|
| Ingresar / Crear cuenta | Nombre real (solo letras, con vocales, sin números, teclazos ni palabras de relleno), correo validado (formato, dominio mal escrito, temporal, que reciba correo) y contraseña segura (10+ caracteres, mayúscula, minúscula, número, símbolo, sin secuencias, sin repetir, no común, sin tu nombre o correo) con indicador de fuerza |
| Inicio | Procesos abiertos hoy, nuevos, por región y tipo de contrato |
| Oportunidades | Coincidencias de tus perfiles con filtros (texto, entidad, región, departamento, modalidad, cierre, valor) y orden; avisa de las nuevas desde tu última visita. Clic derecho en una tarjeta: abrir en SECOP, copiar enlace, ocultar o volver a mostrar (casilla «Mostrar ocultas»; tabla `procesos_ocultos`) |
| Mis búsquedas / Nuevo perfil | Perfiles con campos obligatorios, opción «Todos» y «Otro» para escribir; vista previa de cuántos procesos coinciden |
| Mis postulaciones | Botón «Postularme» en cada proceso abierto. Cada postulación avanza por las 7 etapas de la guía **en orden** (registro en SECOP II, revisar el proceso, leer el pliego, reunir documentos, propuesta económica, presentar la oferta, evaluación): una etapa se desbloquea al completar la anterior. La de documentos se completa sola con lo de Mis documentos más los requisitos propios del pliego; la oferta no puede pasar del presupuesto; no se marca presentada si ya cerró; resultado ganada, no ganada o subsanar (con plazo). Tabla `postulaciones` |
| Mis documentos | Lo que pide SECOP II para presentarse (guía para persona natural): datos del proponente (documento, teléfono, profesión, matrícula COPNIA, RUP, SIGEP...) y subida de archivos (cédula, inhabilidades, seguridad social, antecedentes, RUT, certificación bancaria, RUP, SIGEP, título, tarjeta profesional, experiencia, certificaciones). PDF/JPG/PNG de hasta 5 MB (en modo API van a Supabase Storage; en modo JDBC, a la tabla `documentos`), con fecha de vencimiento y aviso 15 días antes. Muestra para qué modalidades estás listo y qué te falta; las tarjetas de Oportunidades y Buscar también lo dicen |
| Buscar | Búsqueda libre (`"frase"`, `OR`, `-excluir`) o por profesión |
| Planes / Mi cuenta | Plan y límites, avisos, nombre, contraseña y **foto de perfil** (JPG/PNG hasta 10 MB; se recorta en cuadrado y se guarda a 256×256 en la tabla `fotos_perfil`; también se ve en el menú, sin foto muestra las iniciales) |
| Administración | Estadísticas, descargar de SECOP ahora, historial, cambiar plan y rol de usuarios |

## Diferencias con la web original

- Los **correos de alerta** se reemplazaron por **avisos dentro de la app** (en Oportunidades).
- **Pagos con Mercado Pago**, verificación de correo con código y login con Google no están incluidos;
  el administrador asigna los planes.
- La actualización diaria la hace el servidor a las 21:00 de Colombia (en modo JDBC o H2 corre mientras la app
  esté abierta, a la hora de `actualizacion.hora` en `config.properties`).

## Base de datos

- **Supabase por HTTPS** (`bd.modo=api`, el activo): no necesita contraseña; la Edge Function `api` es la
  única que entra a la base.
- **Supabase directo** (`bd.modo=jdbc`): `config.properties` tiene la conexión (Session pooler, esquema `secop`)
  y `config.local.properties` la contraseña. También sirve la variable de entorno `SECOP_BD_PASSWORD`.
- **H2 local** (respaldo): `./data/`. Para empezar de cero, cierra la app y borra la carpeta `data`.
- La barra de abajo de la app dice cuál se está usando (punto naranja = base local).
- Nunca subas a GitHub `config.local.properties` ni `supabase/.temp/` (ya están en el `.gitignore`).
