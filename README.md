# SECOP RADAR — aplicación de escritorio (JavaFX)

Versión de escritorio en **Java 21 + JavaFX (FXML)** del proyecto
[Julianskuu/Secop-radar](https://github.com/Julianskuu/Secop-radar), que originalmente era una web en Next.js.
Descarga los procesos de contratación de **SECOP II** desde datos.gov.co, los cruza con los perfiles de búsqueda
de cada usuario y muestra las oportunidades ordenadas por puntaje.

Todo corre **dentro de la aplicación**: no hay navegador ni servidor web. La base de datos es H2 en un archivo
local (`./data/secop.mv.db`), así que no hay que instalar nada más.

---

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

Tests (36, la mayoría portados de los tests originales):

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
│   ├── BorradorPerfil, GrupoOpciones                                          (formulario de perfiles)
│   ├── dao/        BaseDatos (JDBC + schema.sql), UsuarioDAO, PerfilDAO, ProcesoDAO, CoincidenciaDAO, LogDAO
│   ├── servicio/   Servicios (arma todo), CuentaService, PerfilesService, IngestaService (datos.gov.co),
│   │               CoincidenciasService + Matching (puntaje), OportunidadesService, BusquedaService,
│   │               PortadaService, PlanesService, CronService (actualización diaria), LegalService
│   └── util/       Texto, TextoOracion, Formato, Registro
└── controlador/                   ← CONTROLADOR: un controlador por vista FXML
    ├── Navegador, Pantalla        (carga las vistas y cambia de pantalla)
    ├── LoginControlador, RegistroControlador, PrincipalControlador
    ├── InicioControlador, OportunidadesControlador, TarjetaOportunidadControlador
    ├── PerfilesControlador, TarjetaPerfilControlador, NuevoPerfilControlador, GrupoOpcionesControlador
    └── BuscarControlador, PlanesControlador, CuentaControlador, AdminControlador, LegalControlador

src/main/resources/
├── co/secopradar/vista/           ← VISTA: archivos FXML + estilos.css + imágenes
│   ├── login.fxml, registro.fxml, principal.fxml
│   ├── inicio.fxml, oportunidades.fxml, tarjetaOportunidad.fxml
│   ├── perfiles.fxml, tarjetaPerfil.fxml, nuevoPerfil.fxml, grupoOpciones.fxml
│   └── buscar.fxml, planes.fxml, cuenta.fxml, admin.fxml, legal.fxml
├── config.properties              ← configuración (token de datos.gov.co, hora de actualización...)
├── schema.sql                     ← tablas de la base
├── datos/                         ← catálogos: profesiones y áreas UNSPSC
└── legal/                         ← términos y política de datos
```

- Las vistas (`.fxml`) solo describen la pantalla; se pueden abrir y editar en **Scene Builder**.
- Los controladores responden a los botones y pasan los datos al modelo; no tienen SQL ni reglas de negocio.
- El modelo no sabe nada de JavaFX: valida, calcula y guarda.

## Pantallas

| Pantalla | Qué hace |
|---|---|
| Ingresar / Crear cuenta | Correo validado (formato, dominio mal escrito, temporal, que el dominio reciba correo) |
| Inicio | Procesos abiertos hoy, nuevos, por región y tipo de contrato |
| Oportunidades | Coincidencias de tus perfiles con filtros (texto, entidad, región, departamento, modalidad, cierre, valor) y orden; avisa de las nuevas desde tu última visita |
| Mis búsquedas / Nuevo perfil | Perfiles con campos obligatorios, opción «Todos» y «Otro» para escribir; vista previa de cuántos procesos coinciden |
| Buscar | Búsqueda libre (`"frase"`, `OR`, `-excluir`) o por profesión |
| Planes / Mi cuenta | Plan y límites, avisos, nombre y contraseña |
| Administración | Estadísticas, descargar de SECOP ahora, historial, cambiar plan y rol de usuarios |

## Diferencias con la web original

- Los **correos de alerta** se reemplazaron por **avisos dentro de la app** (en Oportunidades).
- **Pagos con Mercado Pago**, verificación de correo con código y login con Google no están incluidos;
  el administrador asigna los planes.
- La actualización diaria corre mientras la app esté abierta (por defecto a las 21:00, en `config.properties`).

## Base de datos

H2 en `./data/`. Para empezar de cero, cierra la app y borra la carpeta `data`.

> ⚠️ El proyecto está dentro de OneDrive. Si OneDrive borra o deja "solo en línea" archivos del proyecto
> (como pasó con `src/`), muévelo a una carpeta fuera de OneDrive, por ejemplo `C:\Proyectos\Secop`.
