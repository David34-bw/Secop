# Acceso por HTTPS (modo API)

Para redes donde el firewall bloquea PostgreSQL: la app habla por HTTPS (puerto 443) con una Edge
Function de Supabase y no necesita la contraseña de la base.

| Fase | Documento | Estado |
|---|---|---|
| 1. Inventario y endpoints | [fase1-inventario.md](fase1-inventario.md) | Aprobada |
| 2. Interfaces de repositorio | [fase1-inventario.md §9](fase1-inventario.md) | Hecha |
| 3. Edge Function, sesiones y migración | [fase3-funciones.md](fase3-funciones.md) | Archivos locales |
| 4. Implementación Api en Java | [fase4-java.md](fase4-java.md) | Hecha, probada con servidor simulado |
| 5. Despliegue | [fase5-despliegue.md](fase5-despliegue.md) | Guía para ejecutar |
