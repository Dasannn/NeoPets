# NeoPets — Constitución del proyecto

> Estado: **BORRADOR — pendiente de aprobación**
> Fuente de verdad #1. Si otro documento contradice este, gana este.

## 1. Propósito

NeoPets es un plugin de Paper que da al jugador mascotas persistentes que lo
acompañan, progresan y pueden morir y revivir. Objetivo de producto: que el
jugador no se sienta solo en sus primeras horas en el servidor y tenga una
progresión de largo plazo (dungeons → mascotas → skill points).

## 2. Principios (no negociables)

1. **Escalable** — añadir una mascota, una variante, una skill o un rango VIP
   es *configuración o una clase nueva*, nunca un cambio transversal.
2. **Fácil de actualizar de versión de Minecraft** — todo uso de API
   específica de versión (atributos, entidades, Pathfinder/Goals, NMS) vive
   detrás de una interfaz en un módulo aislado (`platform`). El dominio no
   importa `org.bukkit.*`.
3. **Optimizado** — cero I/O de base de datos en el hilo principal; nada de
   tareas por-tick por mascota si se puede evitar (ticks compartidos,
   eventos); caché en memoria de los datos de jugadores conectados.
4. **Sistemas independientes** — *Mascotas* (posesión, invocación, GUI de
   selección, skins) y *Skill tree* (atributos, nodos, habilidades) son
   módulos separados que solo se comunican por interfaces/eventos del
   dominio. Uno puede fallar o desactivarse sin romper al otro.
5. **Todo configurable, todo toggleable** — cada feature técnica tiene un
   flag en `config.yml`, editable también desde la GUI de admin. Valores por
   defecto conservadores (p. ej. compra de mascotas: desactivada).
6. **Anti-abuso por diseño** — ítems especiales (p. ej. el ítem de mascota
   muerta) se identifican con PersistentDataContainer firmado/único y se
   bloquean en crafteo, yunque, horno, smithing, etc.
7. **Permisos explícitos** — todo comando y acción tiene un nodo
   `neopets.*`; lo administrativo bajo `neopets.admin.*`. Límites por rango
   se leen de configuración + LuckPerms (meta/grupos), nunca hardcodeados.

## 3. Stack técnico (propuesta)

| Área | Decisión | Motivo |
|---|---|---|
| Servidor | Paper 26.3 | Requisito |
| Lenguaje | Java 25 | Requerido por Paper 26.x; records, sealed, pattern matching |
| Build | Gradle (Kotlin DSL), multi-módulo, version catalog, toolchain Java 25 | Estándar de Paper; módulos aíslan la parte frágil por versión |
| Plugin loader | `paper-plugin.yml` + Brigadier (Paper command API) | API moderna de Paper |
| Economía | Vault (soft-depend) | Compra de mejoras del skill tree |
| Permisos/rangos | LuckPerms API (soft-depend, fallback a permisos Bukkit) | Límites por rango vía meta |
| Placeholders | PlaceholderAPI (soft-depend) | `%neopets_…%` para scoreboards/menus |
| Persistencia | Capa `Repository` con HikariCP; **MySQL/MariaDB** en producción, **SQLite** como fallback/desarrollo | MySQL permite compartir datos si mañana hay red; SQLite permite probar sin infraestructura |
| Migraciones | Scripts SQL versionados (Flyway o runner propio ligero) | Actualizaciones de esquema seguras |
| Tests | JUnit 5 + MockBukkit (cuando soporte 26.x) para lógica; dominio testeable sin servidor | Calidad sin depender del servidor de pruebas |

## 4. Arquitectura en módulos (alto nivel)

```
neopets-api        → interfaces públicas + eventos (para otros plugins)
neopets-core       → dominio puro: mascotas, variantes, rareza, skill tree, reglas
neopets-storage    → repositorios SQL (MySQL/SQLite), migraciones, caché
neopets-platform   → adaptadores Paper 26.3 (entidades, atributos, IA, GUI)
neopets-plugin     → bootstrap, comandos, listeners, integraciones (Vault/LP/PAPI)
```

Regla de dependencias: `plugin → platform/storage → core → api`. Nunca al
revés. El detalle va en `ARCHITECTURE.md`.

## 5. Flujo de trabajo

1. Orden de documentos: constitución → spec → ARCHITECTURE → plan → tasks.
   No se escribe código de aplicación hasta aprobar el plan.
2. Cada tarea de `docs/tasks.md` es pequeña, con dueño de módulo claro, y se
   trabaja en su propia rama/worktree (trabajo paralelo ordenado).
3. Cada tarea pasa: build + tests + revisión independiente (agente revisor
   distinto al builder) antes de proponer merge.
4. Nada se mergea a `main` sin aprobación explícita del dueño.
5. Las decisiones relevantes se registran en `docs/decisions/` (ADR).

## 6. Entorno

- Servidor de pruebas (Instabox + Crafty) es compartido pero de libre uso
  para pruebas. Credenciales nunca se commitean; viven en los secrets del
  entorno.
- Producción no existe aún; toda prueba en vivo se hace en el servidor de
  pruebas.
