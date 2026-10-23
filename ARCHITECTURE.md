# NeoPets — Arquitectura

> Estado: **APROBADA** (2026-10-23)
> Depende de: `docs/constitution.md`, `docs/spec.md`

## 1. Objetivos de diseño

1. **Aislar lo frágil.** Todo lo que cambia entre versiones de Minecraft (entidades, atributos, IA, variantes) vive solo en `neopets-platform-paper`.
2. **Dominio puro y testeable.** Las reglas (posesión, entrega sin repetir, costes, límites, muerte/revivir) viven en `neopets-core` sin importar `org.bukkit.*`.
3. **Mascotas y skill tree desacoplados.** Se comunican solo mediante interfaces y eventos del dominio.
4. **Extensible por datos.** Una variante nueva = YAML. Un atributo vanilla nuevo = una entrada de catálogo. Una habilidad custom nueva = una clase + registro.
5. **Paralelizable.** Los límites de módulo coinciden con los límites de las tareas, para que varios builders trabajen a la vez sin pisarse.

## 2. Módulos Gradle

```
neopets/
├── settings.gradle.kts
├── gradle/libs.versions.toml          # catálogo de versiones único
├── build-logic/                       # convention plugins (java-25, tests, checkstyle)
├── neopets-api/                       # API pública para otros plugins
├── neopets-core/                      # dominio + servicios (Java puro)
├── neopets-storage/                   # repositorios SQL, migraciones, caché
├── neopets-platform-paper/            # adaptadores Paper 26.3
└── neopets-plugin/                    # bootstrap, comandos, GUIs, integraciones → JAR final
```

Dependencias permitidas (verificadas en build con una regla de Gradle/ArchUnit):

```
neopets-plugin ──► neopets-platform-paper ──► neopets-core ──► neopets-api
       │                                          ▲
       └──────────► neopets-storage ──────────────┘
```

- `core` no depende de Paper, Vault, LuckPerms ni SQL.
- `storage` no depende de Paper.
- Solo `plugin` conoce Vault, LuckPerms, PlaceholderAPI.

Toolchain Java 25 en todos los módulos. JAR final con `paper-plugin.yml`. Las librerías (HikariCP, drivers MySQL/SQLite, Flyway) se descargan en arranque con el `PluginLoader` de Paper (Maven Central), lo que mantiene el JAR pequeño y evita sombrear.

Paquete base: `dev.dasannn.neopets`.

## 3. `neopets-api`

- Interfaces de solo lectura: `NeoPetsApi`, `PetView`, `SkillTreeView`.
- Eventos Bukkit públicos (en `plugin`, declarados aquí como contratos): `PetSummonEvent`, `PetDeathEvent`, `PetReviveEvent`, `PetGiveEvent`, `NodeUnlockEvent`, `NodeActivateEvent`.

## 4. `neopets-core` (dominio)

### 4.1 Modelo
| Tipo | Descripción |
|---|---|
| `PetTypeId`, `VariantId`, `PetId` (UUID) | Identificadores tipados (records). |
| `PetVariant` | Tipo, aspecto (`VariantAppearance`, valor opaco que interpreta la plataforma), `baby`, `weight`, display. |
| `OwnedPet` | Instancia: dueño, variante, nombre, `PetStatus` (ALIVE/DEAD/IN_TRANSIT), progreso del árbol. |
| `PlayerProfile` | Jugador: saldo de SP, mascotas poseídas, mascotas activas. |
| `SkillTreeDefinition` | Por tipo: lista de `AttributeDefinition` (id, categoría VANILLA/CUSTOM, niveles, coste por nivel, efecto). |
| `TreeProgress` | Por mascota: nivel desbloqueado y nivel activo de cada atributo. |
| `RankLimits` | `maxActivePets`, `maxSkillPointsPerPet`, `maxNodesPerPet`. |

### 4.2 Servicios
| Servicio | Responsabilidad |
|---|---|
| `PetService` | Entregar (aleatorio sin repetir, ponderado), invocar/desinvocar con límite de activas, muerte, revivir. |
| `SkillTreeService` | Desbloquear (dinero) y activar (SP) nodos, validando orden, límites y saldo; reset. |
| `SkillPointService` | Saldo de SP por jugador. |
| `SoulItemService` | Emitir/validar/consumir tokens de alma (ID único, un solo uso). |
| `CourierService` | Estado del envío del burro (solicitud, aceptación, timeout, devolución). |

### 4.3 Puertos (interfaces que implementan otros módulos)
| Puerto | Implementación |
|---|---|
| `PetRepository`, `PlayerRepository`, `SoulTokenRepository`, `StorageRepository` | `storage` |
| `Economy` | `plugin` (Vault) |
| `RankLimitsResolver` | `plugin` (LuckPerms + config) |
| `PetEntityGateway` | `platform-paper` (spawn/despawn/aplicar efectos) |
| `Clock`, `RandomSource` | inyectables para tests |

Los servicios solo publican **eventos de dominio** (`PetDied`, `NodeActivated`, …). El skill tree escucha `PetSummoned` para aplicar efectos; el sistema de mascotas no sabe que el skill tree existe.

## 5. `neopets-platform-paper` (lo frágil)

| Componente | Función |
|---|---|
| `PetSpawner` | Crea la entidad, la marca con PDC (`neopets:pet_id`), aplica variante y bebé, la hace no persistente. |
| `VariantApplier<E>` | Uno por tipo (`WolfVariantApplier`, `CatVariantApplier`, `ParrotVariantApplier`, `PolarBearVariantApplier`, `DonkeyVariantApplier`). Traduce `VariantAppearance` a la API de 26.3. |
| `PetBrain` | Goals de seguimiento/teleport/no atacar al dueño (API de Mob Goals de Paper). |
| `AttributeEffectAdapter` | Mapea un id de catálogo (`max_health`, `scale`, `movement_speed`, …) a `Attribute` + `AttributeModifier` con clave propia (`neopets:<attr>`), reaplicable e idempotente. |
| `Ability` (interfaz) + `AbilityRegistry` | Habilidades custom: `Zarpazo`, `Buscatesoros`, `AterrizajeFelino`, `Vigia`, `AlforjasMensajeras`. Cada una con `onEnable`/`onDisable`/`onTick` opcional. |
| `PetTicker` | **Una** tarea repetitiva compartida que recorre las mascotas activas (seguimiento, cooldowns de habilidades). Sin tareas por mascota. |

Para soportar una nueva versión de Minecraft se toca este módulo; si un cambio es grande, se crea `neopets-platform-paper-<versión>` con la misma interfaz.

## 6. `neopets-storage`

- `DataSourceFactory`: HikariCP; `storage.type: mysql | sqlite`.
- Migraciones Flyway en `db/migration/V1__init.sql`, … (mismo SQL compatible con MySQL y SQLite, o variantes por dialecto).
- Tablas iniciales:

| Tabla | Contenido |
|---|---|
| `np_player` | uuid, sp_balance |
| `np_pet` | id, owner, variant_id, name, status, active, created_at |
| `np_pet_attribute` | pet_id, attribute_id, unlocked_level, active_level |
| `np_soul_token` | token_id, pet_id, owner, issued_at, consumed_at |
| `np_pet_storage` | pet_id, contents (blob serializado por la plataforma) |

- **Threading:** todo acceso SQL en un `ExecutorService` propio; nunca en el hilo principal.
- **Caché:** perfil y mascotas cargados en `AsyncPlayerPreLoginEvent`, en memoria mientras el jugador está conectado; escrituras inmediatas asíncronas para operaciones críticas (SP, tokens, dinero) y flush periódico para el resto; descarga al salir.

## 7. `neopets-plugin`

| Paquete | Contenido |
|---|---|
| `bootstrap` | `NeoPetsBootstrap` (registro de comandos Brigadier), `NeoPetsLoader` (librerías), `NeoPetsPlugin` (cableado manual de dependencias). |
| `config` | Carga y validación de `config.yml`, `pets/*.yml`, `skilltrees/*.yml`, `lang/es.yml` (MiniMessage). `config-version` + migración automática. |
| `command` | `/neopets`, `/neopet give|sp|revive|reset|admin|reload`. |
| `gui` | Mini-framework de menús de cofre propio (`Menu`, `Button`, paginación). GUIs: Mis mascotas, Skill tree, Admin features, Envío del burro. |
| `listener` | Muerte, daño PvP, conexión/desconexión, protección del ítem de alma (crafteo, yunque, etc.), protección de entidades. |
| `integration` | `VaultEconomy`, `LuckPermsRankResolver`, `NeoPetsExpansion` (PAPI). Todas opcionales: si el plugin no está, se usa un fallback y se registra un aviso. |

## 8. Configuración (archivos en `plugins/NeoPets/`)

```
config.yml          # features (toggles), storage, ranks, costes globales, timings
pets/<tipo>.yml     # variantes por tipo
skilltrees/<tipo>.yml
lang/es.yml
```

La GUI de admin lee/escribe solo claves declaradas en un `FeatureRegistry` (clave, tipo, rango válido, descripción), para no exponer ni romper otras claves.

## 9. Concurrencia y rendimiento

- Paper (no Folia) en el MVP, pero todo pasa por una interfaz `TaskScheduler` para poder soportar Folia más tarde.
- `core` no asume ningún hilo: los puertos hacia el mundo (`PetEntityGateway`, etc.) son asíncronos (`CompletableFuture`) y su implementación en `platform-paper` ejecuta el trabajo en el hilo del servidor. Se pueden llamar desde cualquier hilo (p. ej. continuaciones del executor de base de datos).
- Un solo ticker de mascotas; las habilidades con área usan `getNearbyEntities` con radio acotado y cooldown.
- Modificadores de atributo aplicados al invocar o al cambiar el árbol, no por tick.

## 10. Tests y calidad

- `core`: JUnit 5, sin servidor. Objetivo: toda regla de la spec tiene test.
- `storage`: tests con SQLite en memoria (y MySQL vía Testcontainers si el CI lo permite).
- `platform-paper` / `plugin`: MockBukkit cuando soporte 26.x; si no, pruebas manuales en el servidor de pruebas con un checklist por tarea.
- CI: GitHub Actions `./gradlew build` en cada push.

## 11. Cómo se reparte el trabajo en paralelo

| Carril | Módulo | Depende de |
|---|---|---|
| A | build-logic + esqueleto de módulos | — |
| B | core: modelo + PetService | A |
| C | core: SkillTreeService + SkillPointService | A |
| D | storage | B, C (solo interfaces de puertos) |
| E | platform: spawn, variantes, brain | B |
| F | platform: atributos + habilidades custom | C |
| G | plugin: config, comandos, integraciones | B, C |
| H | plugin: GUIs | G |

Los puertos de `core` se definen primero (carril A/B/C) para que D–H trabajen contra interfaces estables.
