# SECOP RADAR — versión Java

Port a **Java 21 + Spring Boot 3** del proyecto [Julianskuu/Secop-radar](https://github.com/Julianskuu/Secop-radar)
(originalmente Next.js + PostgreSQL). Descarga a diario los procesos de contratación de **SECOP II** desde
datos.gov.co, los cruza con los perfiles de búsqueda de cada usuario y muestra las oportunidades ordenadas por
puntaje.

Corre **en local sin instalar nada más**: la base de datos es H2 en archivo (`./data/secop.mv.db`).

---

## Cómo abrirlo y correrlo en IntelliJ IDEA

1. **Abrir el proyecto**: `File > Open...` y elige la carpeta del proyecto (la que tiene `pom.xml`).
   Si pregunta, elige **"Open as Project"** y **"Trust Project"**. IntelliJ detecta Maven y descarga las
   dependencias solo (barra de progreso abajo a la derecha).
2. **JDK 21**: `File > Project Structure > Project > SDK` → elige **21** (por ejemplo `temurin-21`).
   Si no aparece: `Add SDK > Download JDK...` → versión 21, cualquier proveedor (Eclipse Temurin está bien).
3. **Ejecutar**: abre `src/main/java/co/secopradar/SecopRadarApplication.java` y presiona el ▶ verde junto a
   `main` → **Run 'SecopRadarApplication'**.
4. Abre **http://localhost:8080**

La primera vez que arranca con la base vacía, descarga en segundo plano los procesos de los últimos 7 días
(tarda segundos; míralo en la consola de IntelliJ: `[ingesta] lote 1: ...`).

> **La primera cuenta que registres queda como administradora** (ve a `/admin`). Las siguientes son usuarios
> normales con 30 días de prueba del plan Profesional.

### Desde la terminal (opcional)

```bash
mvnw.cmd spring-boot:run
```

Tests (29, portados de los tests originales de Node):

```bash
mvnw.cmd test
```

---

## Qué incluye

| Original (Next.js) | Versión Java |
|---|---|
| `worker/ingesta.mjs` (descarga de datos.gov.co) | `IngestaService` |
| `worker/coincidencias.mjs` + `lib/matching.js` | `CoincidenciasService` + `Matching` (mismo puntaje) |
| `worker/planes.mjs` + `lib/limites.js` | `PlanesService` |
| `worker/alertas.mjs` (correo) | `AlertasService` (opcional, requiere SMTP) |
| GitHub Action diario (`cron.yml`) | `CronService` con `@Scheduled` (9:00 p. m. hora Colombia) + botón en `/admin` |
| Portada con datos en vivo | `/` |
| Búsqueda pública | `/buscar` (texto libre con `"frase"`, `OR`, `-excluir`, o por profesión) |
| Panel con filtros | `/dashboard` (región, departamento, modalidad, entidad, cierre, valor, orden) |
| Perfiles (profesión, áreas UNSPSC, palabras clave, exclusiones...) | `/perfiles` con vista previa en vivo |
| Planes y límites (Gratis / Profesional / Empresa / prueba 30 días) | Igual (`Plan.efectivo`) |
| Términos y política de datos | `/terminos`, `/privacidad` |
| Admin | `/admin` (estadísticas, corridas, asignar plan y rol, actualizar ahora) |

### Diferencias con el original

- **Pagos con Mercado Pago**: no incluidos. En su lugar, un admin asigna el plan desde `/admin`.
- **Verificación de correo con código** y **login con Google**: no incluidos (Google ya estaba apagado en el
  original). Las cuentas quedan verificadas al registrarse.
- **Búsqueda de texto**: el original usaba `tsvector`/`websearch_to_tsquery` de PostgreSQL; aquí se hace en
  Java con las mismas reglas del cruce (palabra completa, plural simple, sin tildes), así funciona igual en H2.
- **Límite de intentos** (login, registro, búsqueda): en memoria en vez de una tabla.

---

## Configuración (`src/main/resources/application.properties`)

| Propiedad | Para qué |
|---|---|
| `secop.socrata-app-token` | Token opcional de datos.gov.co (sube el límite de la API) |
| `secop.ingesta.dias-atras` | Días que descarga cada corrida diaria (5) |
| `secop.cron` | Hora de la corrida diaria (`0 0 21 * * *`, hora de Colombia) |
| `spring.mail.*` + `secop.alertas.enviar=true` | Activa las alertas por correo (p. ej. Gmail con contraseña de aplicación) |
| `secop.secreto` | Clave para firmar los enlaces de "dejar de recibir alertas" |

**Base de datos**: H2 en `./data/`. Consola web en http://localhost:8080/h2-console
(JDBC URL `jdbc:h2:file:./data/secop`, usuario `sa`, sin contraseña). Para empezar de cero, detén la app y
borra la carpeta `data`.

**PostgreSQL (opcional)**: crea la base `secop_radar`, ajusta `application-postgres.properties` y activa el
perfil `postgres` (en IntelliJ: *Edit Configurations > Active profiles: postgres*).

---

## Estructura

```
src/main/java/co/secopradar/
├── SecopRadarApplication.java   ← main
├── dominio/      Plan, Perfil, Proceso, Usuario, Region, RangoValor, Modalidades, Catalogo
├── repositorio/  Acceso a datos con JdbcTemplate
├── servicio/     Ingesta, cruce (Matching), panel, búsqueda, portada, alertas, cron
├── util/         Normalización de texto, tipo oración, formatos de pesos y fechas
└── web/          Controladores, sesión y protección de rutas
src/main/resources/
├── schema.sql    Tablas (compatibles con H2 y PostgreSQL)
├── datos/        Catálogos: profesiones, segmentos/familias/clases UNSPSC
├── legal/        Términos y política de datos (Markdown)
├── templates/    Páginas Thymeleaf
└── static/       CSS, JS e imágenes
```
# Secop
# Secop
