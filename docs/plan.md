# NeoPets — Plan de implementación (MVP)

> Estado: **APROBADO** (2026-10-23)
> Depende de: `docs/constitution.md`, `docs/spec.md`, `ARCHITECTURE.md`

## 1. Hitos

| Hito | Resultado verificable | Carriles (ARCHITECTURE §11) |
|---|---|---|
| **M0 Fundaciones** | `./gradlew build` verde en CI; el JAR vacío carga en el servidor de pruebas (Paper 26.3, Java 25) y muestra "NeoPets enabled". | A |
| **M1 Dominio** | `core` con modelo, puertos y servicios de mascotas, SP y skill tree; reglas de la spec cubiertas por tests unitarios. | B, C |
| **M2 Persistencia** | `storage` con MySQL y SQLite, migraciones, caché por jugador; tests con SQLite. | D |
| **M3 Mascotas jugables** | Config de variantes, `/neopet give`, GUI "Mis mascotas", invocar/desinvocar, seguimiento, límites por rango. Probado en servidor. | E, G, H |
| **M4 Skill tree jugable** | Atributos vanilla aplicados, desbloquear con Vault + activar con SP, GUI del árbol. Probado en servidor. | F, G, H |
| **M5 Habilidades únicas** | Zarpazo, Buscatesoros, Aterrizaje felino, Vigía, Alforjas mensajeras. | F, H |
| **M6 Muerte y revivir** | Toggles de muerte/PvP, ítem de alma inmutable, `neopets revive`, integración LDPotions. | B, F, G |
| **M7 Admin y cierre** | GUI de features, PlaceholderAPI, `/neopet reload`, mensajes `lang/es.yml`, checklist de pruebas completo en el servidor. | G, H |

Los hitos M3–M6 pueden avanzar en paralelo una vez cerrados M1 y M2 (los puertos ya estables).

## 2. Flujo por tarea

1. Claude toma una tarea de `docs/tasks.md` cuyas dependencias estén cerradas.
2. Crea un worktree local `../neopets-wt/<task-id>` en la rama local `task/<task-id>` desde la rama de integración.
3. **Builder** (Codex `gpt-5.6-sol`, `high`) implementa la tarea con sus tests, siguiendo constitución → spec → arquitectura → plan → tareas.
4. **Revisor** (Codex `gpt-6.1-sol`, `high`) revisa el diff contra la spec y la arquitectura; el builder corrige.
5. Claude verifica: `./gradlew build`, reglas de dependencias entre módulos, diff limpio sin archivos ajenos.
6. Claude integra la tarea en la rama de integración `claude/gallant-cray-3eber3` y hace push. Solo se pushea esa rama.
7. Las tareas con prueba en vivo se despliegan en el servidor de pruebas y se valida su checklist.
8. Nada llega a `main` sin aprobación explícita del dueño.

Máximo 3 tareas en paralelo, siempre en módulos o paquetes distintos.

## 3. Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Coordenadas/API de Paper 26.3 distintas a lo esperado | T01 fija la versión exacta y la valida contra el servidor antes de cualquier otra tarea. |
| MockBukkit sin soporte 26.x | Lógica en `core` (testeable sin servidor); checklist manual en servidor para `platform`/`plugin`. |
| Duplicación de ítems (alma, alforjas) | Tokens de un solo uso en BD; un solo visor por cofre; tests específicos. |
| Servidor de pruebas compartido | Despliegues anunciados en el log de la tarea; nunca tocar otros plugins ni mundos. |
| Plugins privados (LDPotions, LDActivities) | Integración solo por comandos; no dependemos de su código. |

## 4. Definición de terminado (MVP)

- Todas las tareas de `docs/tasks.md` cerradas.
- `./gradlew build` verde en CI.
- Checklist de pruebas en vivo completo en el servidor de pruebas.
- Documentación de usuario en `docs/guides/` (comandos, permisos, config).
