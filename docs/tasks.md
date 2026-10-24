# NeoPets — Tareas (MVP)

> Estado: **APROBADO** (2026-10-23)
> Formato: ID · carril · depende de · alcance · terminado cuando…

## M0 Fundaciones

- **T01** · A · — · Esqueleto Gradle: `settings.gradle.kts`, `gradle/libs.versions.toml`, `build-logic` (Java 25 toolchain, JUnit 5), los 5 módulos vacíos, wrapper. Fijar versión exacta de `paper-api` 26.3. · `./gradlew build` verde.
- **T02** · A · T01 · Regla de dependencias entre módulos (test ArchUnit o check de Gradle) según ARCHITECTURE §2. · Un import prohibido rompe el build.
- **T03** · A · T01 · `paper-plugin.yml`, `NeoPetsBootstrap`, `NeoPetsLoader` (descarga HikariCP/drivers/Flyway), `NeoPetsPlugin` vacío. Soft-depends: Vault, LuckPerms, PlaceholderAPI. · El JAR carga en el servidor de pruebas.
- **T04** · A · T01 · CI GitHub Actions: `./gradlew build` en cada push. · Workflow verde.

## M1 Dominio (`neopets-core`, `neopets-api`)

- **T05** · B · T02 · Modelo: IDs tipados, `PetVariant`, `OwnedPet`, `PetStatus`, `PlayerProfile`, `RankLimits`. Puertos: repositorios, `Economy`, `RankLimitsResolver`, `PetEntityGateway`, `Clock`, `RandomSource`. Eventos de dominio. · Compila; tests de invariantes.
- **T06** · B · T05 · `PetService`: entrega ponderada sin repetir (lista, random, random por tipo, específica), invocar/desinvocar con `maxActivePets`. · Tests de todas las reglas de spec §3.2 y §4.
- **T07** · C · T05 · `SkillTreeDefinition`, `AttributeDefinition`, `TreeProgress`; `SkillPointService`. · Tests.
- **T08** · C · T07 · `SkillTreeService`: desbloquear (dinero, orden, `maxNodesPerPet`), activar (SP, `maxSkillPointsPerPet`), reset (devuelve SP). · Tests de spec §5.4.
- **T09** · B · T06 · Muerte/revivir en dominio + `SoulItemService` (token de un solo uso, `keep-skills`). · Tests de spec §3.5.
- **T10** · B · T06 · `CourierService` (solicitud, aceptación, timeout, devolución, un solo visor). · Tests de spec §5.3.
- **T11** · B · T05 · `neopets-api`: `NeoPetsApi`, vistas y contratos de eventos públicos. · Compila; javadoc.

## M2 Persistencia (`neopets-storage`)

- **T12** · D · T05, T07 · `DataSourceFactory` (MySQL/SQLite), migración `V1__init.sql` con las tablas de ARCHITECTURE §6. · Migración corre en SQLite en tests.
- **T13** · D · T12 · Implementación de repositorios + executor asíncrono. · Tests CRUD con SQLite.
- **T14** · D · T13 · Caché por jugador (carga en pre-login, flush periódico, guardado al salir, escrituras críticas inmediatas). · Tests.

## M3 Mascotas jugables

- **T15** · G · T03, T06 · Carga y validación de `config.yml`, `pets/*.yml`, `lang/es.yml` (MiniMessage), `config-version`. Variantes por defecto de los 5 tipos. · Config inválida da error claro; tests de parseo.
- **T16** · E · T05 · `PetSpawner` + `VariantApplier` de los 5 tipos (incl. bebé permanente), PDC, limpieza de huérfanas. · Checklist en servidor: cada variante aparece correcta.
- **T17** · E · T16 · `PetBrain` (seguir, teleport, no hostil al dueño, protección contra terceros) + `PetTicker` único. · Checklist en servidor.
- **T18** · G · T15 · `LuckPermsRankResolver` + fallback por permisos. · Tests con resolver falso; checklist.
- **T19** · G · T06, T14, T15 · Comandos `/neopet give …` y `/neopets` con Brigadier y permisos `neopets.*`. · Checklist.
- **T20** · H · T19, T17 · Mini-framework de GUI + GUI "Mis mascotas" (paginada, invocar/desinvocar, renombrar). · Checklist.

## M4 Skill tree jugable

- **T21** · F · T08, T16 · `AttributeEffectAdapter` + catálogo vanilla; aplicar al invocar/cambiar árbol. `skilltrees/*.yml` de los 5 tipos (22 nodos). · Checklist: cada atributo cambia el valor real.
- **T22** · G · T08 · `VaultEconomy` + `/neopet sp give|take|set`. · Checklist.
- **T23** · H · T20, T21, T22 · GUI del skill tree (colores por estado, costes, límites). · Checklist.

## M5 Habilidades únicas

- **T24** · F · T21 · `Ability` + `AbilityRegistry` + Zarpazo (oso) + Vigía (loro). · Checklist.
- **T25** · F · T24 · Buscatesoros (perro) + Aterrizaje felino (gato). · Checklist.
- **T26** · F/H · T24, T10, T20 · Alforjas mensajeras (burro): cofre persistente, GUI de envío, aceptación, devolución, anti-duplicación. · Checklist + prueba con 2 jugadores.

## M6 Muerte y revivir

- **T27** · G · T09, T17 · Listeners de muerte/PvP con toggles, entrega del ítem de alma (inventario → suelo). · Checklist.
- **T28** · G · T27 · Protección del ítem de alma (crafteo, yunque, smithing, horno, grindstone, encantar, colocar) + `neopets revive <jugador>`. · Checklist incluido intento de abuso.

## M7 Admin y cierre

- **T29** · H · T20 · `FeatureRegistry` + GUI de admin de features (escribe `config.yml` y recarga) + `/neopet reload`. · Checklist.
- **T30** · G · T19 · Expansión PlaceholderAPI (spec §8). · Checklist.
- **T31** · — · todo · Guía de usuario en `docs/guides/` (comandos, permisos, config) + checklist final en servidor. · Revisado por el dueño.
