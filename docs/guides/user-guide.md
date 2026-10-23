# Guía de administración de NeoPets

Esta guía describe el código incluido en este repositorio. Para comprobarlo en un
servidor, sigue el [checklist de pruebas](live-checklist.md). Los archivos de
configuración citados viven en `plugins/NeoPets/`.

## Qué es e instalación

NeoPets ofrece mascotas persistentes de cinco tipos: perro (`dog`), gato (`cat`),
loro (`parrot`), oso polar (`polar_bear`) y burro (`donkey`). Cada variante se posee
una sola vez; puede invocarse, renombrarse, mejorar su árbol, morir y revivir.
La colección no tiene un límite de variantes; los rangos limitan las invocaciones
y las mejoras de cada mascota.

1. Usa Paper 26.3 con Java 25. Este adaptador está destinado a Paper, no a Folia.
2. Compila con `./gradlew build` y copia el JAR `NeoPets-*.jar` de
   `neopets-plugin/build/libs/` a `plugins/` con el servidor detenido.
3. Instala opcionalmente Vault **y un proveedor de economía**, LuckPerms y
   PlaceholderAPI. Son dependencias suaves: NeoPets puede arrancar sin ellas.
   Sin economía no se pueden comprar desbloqueos; los SP y las mascotas siguen
   disponibles. Sin LuckPerms se usan permisos Bukkit. Sin PlaceholderAPI no se
   registra la expansión.
4. Arranca una vez: se copian `config.yml`, `lang/es.yml`, los cinco archivos de
   `pets/` y los cinco de `skilltrees/`. El cargador descarga las bibliotecas SQL
   desde Maven Central; necesita acceso a la red al resolverlas por primera vez.
5. El valor inicial `storage.type: sqlite` crea `neopets.db` en la carpeta del
   plugin. Para MySQL/MariaDB, prepara una base y usuario con permisos para crear
   y modificar las tablas, configura `storage.mysql` y reinicia. Las migraciones
   de esquema SQL se ejecutan al arrancar. Cambiar de motor no copia los datos.
6. Comprueba `NeoPets enabled (version …)` y concede los permisos siguientes.
   Conserva una copia de los YAML y de la base antes de actualizar.

## Comandos y permisos

`Ana` representa un jugador real. En consola se omite `/`. `give` y `sp` admiten
jugadores desconectados que ya hayan jugado; `revive` y `reset` exigen que estén conectados.
Las sugerencias de nombres solo muestran conectados.

| Sintaxis | Permiso exigido | Consola | Ejemplo |
|---|---|---|---|
| `/neopets` (alias `/pets`) | `neopets.use` | No | `/pets` |
| `/neopet summon <variante\|UUID-mascota>` | `neopets.use` | No | `/neopet summon dog_pale` |
| `/neopet dismiss <variante\|UUID-mascota>` | `neopets.use` | No | `/neopet dismiss dog_pale` |
| `/neopet give <jugador> <id>` | `neopets.admin.give` | Sí | `/neopet give Ana dog_pale` |
| `/neopet give <jugador> <id1,id2,…>` | `neopets.admin.give` | Sí | `/neopet give Ana dog_black,dog_snowy` |
| `/neopet give <jugador> random [tipo]` | `neopets.admin.give` | Sí | `/neopet give Ana random cat` |
| `/neopet sp give <jugador> <cantidad>` | `neopets.admin.sp` | Sí | `/neopet sp give Ana 3` |
| `/neopet sp take <jugador> <cantidad>` | `neopets.admin.sp` | Sí | `/neopet sp take Ana 1` |
| `/neopet sp set <jugador> <cantidad>` | `neopets.admin.sp` | Sí | `/neopet sp set Ana 0` |
| `/neopets revive <jugador>` (también `/pets revive`) | `neopets.admin.revive` | Sí | `/neopets revive Ana` |
| `/neopet courier accept <UUID-solicitud>` | `neopets.use` | No | `/neopet courier accept <UUID del botón recibido>` |
| `/neopet courier reject <UUID-solicitud>` | `neopets.use` | No | `/neopet courier reject <UUID del botón recibido>` |
| `/neopet reset <jugador> <petId\|petName>` | `neopets.admin.reset` | Sí | `/neopet reset Ana dog_pale` |
| `/neopet admin` | `neopets.admin.gui` | No | `/neopet admin` |
| `/neopet reload` | `neopets.admin.reload` | Sí | `/neopet reload` |

Los UUID de mensajería los incluyen los botones del chat; solo el destinatario
puede responder. `give`/`take` requieren entero positivo; `set` admite cero.
El saldo usa enteros hasta `2147483647`; quitar más de lo disponible o desbordarlo
se rechaza. No hay un comando de compra ni renombrado; el renombrado y el reset de jugador
se hacen en la GUI. El reset administrativo es gratuito, devuelve los SP asignados
y borra los desbloqueos como el reset existente, sin devolver dinero. Acepta UUID
persistente, ID de variante o nombre personalizado (también con espacios).
Si varios nombres coinciden, usa el UUID para seleccionar la mascota exacta. `revive` pertenece a la
raíz plural `/neopets`, no a `/neopet`.

`paper-plugin.yml` declara dependencias, versión de API y `neopets.admin`
(predeterminado para operadores), con `neopets.admin.reset` como hijo.
Los comandos se registran en ejecución y comprueban estos nodos:

| Permiso | Alcance |
|---|---|
| `neopets.use` | Menú de mascotas, invocar, guardar y responder mensajería; acceso a las alforjas |
| `neopets.admin.give` | Entregar variantes |
| `neopets.admin.reset` | Reset gratuito del árbol de una mascota de un jugador conectado |
| `neopets.admin.sp` | Dar, quitar y fijar saldo de SP |
| `neopets.admin.revive` | Ejecutar la integración de revivir para un jugador |
| `neopets.admin.gui` | Abrir y editar la GUI administrativa |
| `neopets.admin.reload` | Recargar YAML |
| `neopets.rank.<id>` | Seleccionar un rango **definido** en `ranks` |

`neopets.admin` hereda `neopets.admin.reset`; concede los demás nodos concretos
para las otras acciones administrativas. Los comodines dependen del gestor de permisos. Por ejemplo, en consola con LuckPerms:

```text
lp group default permission set neopets.use true
lp user Ana permission set neopets.admin.gui true
lp user Ana permission set neopets.admin.reload true
```

### Límites por rango

`ranks.default` siempre se aplica. Se toma el máximo **de cada campo** entre
`default` y los rangos reconocidos del jugador. Con LuckPerms se reconocen grupos
heredados cuyo nombre coincida con un ID de `ranks`, y permisos
`neopets.rank.<id>`. No se leen metadatos numéricos de LuckPerms. Sin LuckPerms
solo se consultan esos permisos Bukkit, mediante instantáneas al entrar, cambiar
de mundo y periódicamente. Un jugador offline sin datos de LuckPerms cargados
queda con los límites predeterminados.

| Clave bajo `ranks.<id>` | Tipo / rango | Predeterminado de `default` | Efecto |
|---|---|---|---|
| `max-active-pets` | Entero 0–2147483647 | 1 | Mascotas invocadas simultáneamente |
| `max-skill-points` | Entero 0–2147483647 | 3 | SP asignados **por mascota**, no saldo del jugador |
| `max-nodes-per-pet` | Entero 0–2147483647 | 10 | Nodos desbloqueados por mascota |

Los borradores comentados `vip` (2/6/16) y `vipplus` (4/9/22) no están activos.
Para usarlos, descomenta su sección, recarga y asigna el grupo correspondiente
o ejecuta `lp user Ana permission set neopets.rank.vip true`. Los IDs son claves
minúsculas con números y guiones bajos. Ningún rango adicional tiene valores
implícitos: define sus tres límites.

## Referencia de `config.yml`

Se indican los valores del archivo distribuido, no valores de recuperación de
un YAML inválido. Las secciones son mapas; las hojas siguientes son todas las
claves operativas. Los booleanos admiten `true`/`false`; los decimales deben ser
finitos. `I64` significa `9223372036854775807` y `Dmax`, aproximadamente
`1.7976931348623157e308`.

### Esquema, almacenamiento y carga

| Clave | Tipo | Predeterminado | Rango / efecto |
|---|---|---|---|
| `config-version` | Entero | 16 | Versiones 1–15 migran a 16; una futura se rechaza. Solo cambia al evolucionar el esquema; no la subas manualmente. |
| `storage.type` | Texto | `sqlite` | `sqlite` o `mysql`; selección del motor al arrancar. SQLite es la opción aislada para pruebas; MySQL, para redes de producción. |
| `storage.sqlite.file` | Texto | `neopets.db` | No vacío; ruta del archivo SQLite, relativa a la carpeta del plugin si no es absoluta. |
| `storage.mysql.host` | Texto | `127.0.0.1` | No vacío; host MySQL/MariaDB. |
| `storage.mysql.port` | Entero | 3306 | 1–65535; puerto SQL. |
| `storage.mysql.database` | Texto | `neopets` | No vacío; base SQL. |
| `storage.mysql.username` | Texto | `neopets` | No vacío; usuario SQL. |
| `storage.mysql.password` | Texto | `change-me` | Puede estar vacío; contraseña SQL. |
| `player-data.load-timeout-seconds` | Entero | 10 | 1–I64; espera máxima de carga asíncrona antes del login. |
| `rank-resolution.bukkit-refresh-interval-seconds` | Entero | 30 | 1–86400; refresco de permisos online sin LuckPerms, en el hilo del servidor. |

Se validan también los datos del motor no seleccionado. Cambiar almacenamiento,
proveedores de integración o el intervalo del refresco Bukkit requiere reinicio.

### Mascotas, muerte y reset

| Clave | Tipo | Predeterminado | Rango / efecto |
|---|---|---|---|
| `death.enabled` | Booleano | `true` | Permite muerte; `false` cancela daño letal y deja 1 de vida; no cancela todo el daño. |
| `death.pvp` | Booleano | `false` | Permite daño de otros jugadores; el dueño nunca puede dañar su propia mascota. |
| `death.keep-skills` | Booleano | `true` | Conserva árbol; `false` borra progreso y devuelve SP durante el procesamiento de la muerte. No devuelve dinero. |
| `death.soul-item.material` | Texto | `ECHO_SHARD` | Material de inventario válido en MAYÚSCULAS, distinto de aire. |
| `death.soul-item.name` | Texto MiniMessage | `<light_purple>Alma de <pet></light_purple>` | No vacío; nombre del alma. |
| `death.soul-item.lore` | Lista de textos MiniMessage | `['<gray>Tipo: <type></gray>', '<gray>Úsala con una poción de revivir</gray>']` | Lista de cadenas no vacías; lore del alma. Nombre/lore sustituyen `<pet>` y `<type>`. |
| `shop.enabled` | Booleano | `false` | Reservado para una futura tienda de mascotas; todavía no hay flujo de compra y no tiene efecto. No aparece en la GUI de administración. |
| `rename.enabled` | Booleano | `true` | Habilita renombrado por chat desde la GUI. |
| `reset.player-enabled` | Booleano | `false` | Habilita botón de reset del árbol para jugadores. |
| `give.all-owned-command` | Texto | `""` | Comando de consola si ya posee todos los candidatos, además del mensaje `pets.all-owned` (o `pets.already-owned` para ID específico). Vacío desactiva; sustituye `<player>` y `<uuid>`. Sin `/` inicial; no aparece en GUI admin. |
| `reset.cost` | Decimal | 5000 | 0–Dmax; coste del reset, cero permite reset gratuito. |
| `follow.teleport-distance` | Decimal | 20 | Mayor que 0 y finito; distancia de teleport al dueño. Se fija al arrancar: reinicia para aplicarla. |

### Habilidades

Todos los flags siguientes empiezan en `true`. Además del flag, el nodo único
debe estar activado con SP y la mascota invocada. No existe
`abilities.courier.enabled`: Alforjas depende de su nodo.

| Clave | Tipo | Predeterminado | Rango / efecto |
|---|---|---|---|
| `abilities.aterrizaje-felino.enabled` | Booleano | `true` | Protección de caída del dueño con gato invocado. |
| `abilities.zarpazo.enabled` | Booleano | `true` | Ataque automático del oso. |
| `abilities.zarpazo.cooldown-seconds` | Entero | 10 | 1–86400; intervalo entre intentos. |
| `abilities.zarpazo.radius` | Decimal | 6.0 | >0–128; radio desde la mascota, en bloques. |
| `abilities.zarpazo.damage` | Decimal | 4.0 | >0–2048; daño por objetivo. |
| `abilities.vigia.enabled` | Booleano | `true` | Alertas automáticas del loro. |
| `abilities.vigia.cooldown-seconds` | Entero | 5 | 1–86400; intervalo de detección. |
| `abilities.vigia.radius` | Decimal | 16.0 | >0–128; radio base alrededor del dueño, más Alcance de alerta activo. |
| `abilities.vigia.duration-seconds` | Entero | 5 | 1–3600; duración del brillo vanilla, visible para todos los jugadores cercanos (limitación de la API de Paper). |
| `abilities.buscatesoros.enabled` | Booleano | `true` | Escaneo automático del perro. |
| `abilities.buscatesoros.cooldown-seconds` | Entero | 30 | 1–86400; intervalo entre búsquedas. |
| `abilities.buscatesoros.base-radius` | Decimal | 16.0 | >0–128; radio desde el perro, más Olfato activo. |
| `abilities.buscatesoros.duration-seconds` | Entero | 5 | 1–3600; duración de cada marca de partículas. |
| `abilities.buscatesoros.max-targets` | Entero | 32 | 1–512; máximo de objetivos por búsqueda. |
| `abilities.buscatesoros.max-scan-blocks` | Entero | 8192 | 1–262144; presupuesto de consultas de chunks y posiciones inspeccionadas por activación. |
| `abilities.buscatesoros.ore-materials` | Lista de textos | Lista debajo | No vacía; materiales de bloque válidos en MAYÚSCULAS que cuentan como mena. |

Lista inicial de `ore-materials`:

```yaml
[ANCIENT_DEBRIS, COAL_ORE, COPPER_ORE, DEEPSLATE_COAL_ORE,
 DEEPSLATE_COPPER_ORE, DEEPSLATE_DIAMOND_ORE, DEEPSLATE_EMERALD_ORE,
 DEEPSLATE_GOLD_ORE, DEEPSLATE_IRON_ORE, DEEPSLATE_LAPIS_ORE,
 DEEPSLATE_REDSTONE_ORE, DIAMOND_ORE, EMERALD_ORE, GOLD_ORE, IRON_ORE,
 LAPIS_ORE, NETHER_GOLD_ORE, NETHER_QUARTZ_ORE, REDSTONE_ORE]
```

### Rangos y mensajería

`ranks` y sus tres hojas por rango se detallan en [Límites por rango](#límites-por-rango).

| Clave | Tipo | Predeterminado | Rango / efecto |
|---|---|---|---|
| `courier.request-timeout-seconds` | Entero | 300 | 1–I64; plazo total desde la solicitud, también limita una entrega aceptada. Aceptar no reinicia el plazo. |
| `courier.cooldown-seconds` | Entero | 900 | 0–I64; espera por burro desde el envío, incluso si se rechaza; cero elimina la espera. |

El escáner consume presupuesto por consultas de chunks y posiciones, primero las
más cercanas; al agotar el presupuesto quedan fuera las posiciones más lejanas.

Los máximos de duración son límites de representación, no valores operativos
aconsejados: una fecha de expiración extremadamente lejana puede desbordarse.

## Variantes y entrega ponderada

Los cinco archivos `pets/<tipo>.yml` contienen `type` y un mapa `variants`.
IDs incluidos (los sufijos indican el aspecto; no son nombres traducidos):

| Tipo | IDs distribuidos | Icono configurado |
|---|---|---|
| `dog` | `dog_pale`, `dog_spotted`, `dog_snowy`, `dog_black`, `dog_ashen`, `dog_rusty`, `dog_woods`, `dog_chestnut`, `dog_striped`, `dog_pale_baby`, `dog_black_baby` | `BONE` |
| `cat` | `cat_tabby`, `cat_black`, `cat_red`, `cat_siamese`, `cat_british_shorthair`, `cat_calico`, `cat_persian`, `cat_ragdoll`, `cat_white`, `cat_jellie`, `cat_all_black`, `cat_tabby_baby`, `cat_calico_baby` | `COD` |
| `parrot` | `parrot_red_blue`, `parrot_blue`, `parrot_green`, `parrot_yellow_blue`, `parrot_gray` | `FEATHER` |
| `polar_bear` | `polar_bear`, `polar_bear_baby` | `SNOWBALL` |
| `donkey` | `donkey`, `donkey_baby` | `CHEST` |

Para añadir una variante, agrega una entrada en el archivo de un tipo existente
con ID global único en minúsculas, números y guiones bajos; después recarga:

```yaml
# Dentro de variants: en pets/dog.yml
  dog_black_azul:
    appearance: { variant: black, collar-color: blue }
    baby: false
    weight: 2
    display-name: "Perro negro de collar azul"
    icon: WOLF_SPAWN_EGG
```

`baby` es booleano obligatorio; las crías de entidades que admiten edad permanecen
bebés. Los loros no tienen forma bebé vanilla. `weight` es un número finito
estrictamente positivo. `display-name` es texto no vacío. `icon` es opcional y
acepta un material de inventario válido en mayúsculas; sin él se usa el huevo del
tipo. No cambies los IDs ya entregados: las mascotas persistidas los referencian.

En `appearance`, perro exige `variant` entre `pale`, `spotted`, `snowy`, `black`,
`ashen`, `rusty`, `woods`, `chestnut`, `striped`; gato exige `type` entre `tabby`,
`black`, `red`, `siamese`, `british_shorthair`, `calico`, `persian`, `ragdoll`,
`white`, `jellie`, `all_black`. Ambos permiten `collar-color` con un color vanilla
en minúsculas, como `blue` o `light_blue`. Loro exige `variant` entre `red`, `blue`,
`green`, `cyan`, `gray` (el ID `parrot_yellow_blue` usa `cyan`). Burro y oso exigen
`appearance: {}`. Añadir especies nuevas requiere código; no se descubren otros
archivos de tipos automáticamente.

`give` excluye lo que el jugador ya posee, incluso muerto. En una lista separada
por comas, sin espacios, o en `random [tipo]`, sortea con los pesos de los
candidatos restantes: peso 10 frente a 2 equivale a una relación 5:1, no a
porcentajes fijos. Un ID específico no sortea. Si ya tiene toda la selección,
se muestra el mensaje configurable y se ejecuta una vez el comando de consola
`give.all-owned-command` si está configurado. También se aplica a un ID específico
ya poseído; una entrega exitosa o una selección inválida no ejecuta compensación.

## Uso de mascotas y árbol

`/pets` abre «Mis mascotas»: clic izquierdo invoca/guarda, derecho abre el árbol,
Shift+clic inicia renombrado en chat (1–32 caracteres visibles, sin saltos de
línea; `cancelar` cancela). En un burro con Alforjas activas, Shift+clic derecho
abre las alforjas. El menú tiene filtros por tipo y páginas. La mascota activa
brilla; la muerta usa tinte gris y nombre rojo. No se pueden extraer los botones.

Las mascotas siguen al dueño y se teletransportan al alejarse. Los terceros no
pueden robarlas, montarlas, criarlas o domesticarlas mediante interacciones
normales protegidas por el plugin. Al salir se retiran
sus entidades; al entrar se restauran las activas. El arranque y la carga de
chunks limpian entidades marcadas huérfanas.

Cada mascota tiene progreso propio, con el árbol de su tipo. Hay 22 nodos por
defecto. Primero se **desbloquea** un nivel pagando dinero y luego se **activa**
asignando SP; ambos pasos siguen el orden de niveles de su atributo. Desbloquear
no consume SP ni da el efecto. El saldo SP es compartido por jugador y no tiene
el tope de rango de una mascota. Todos los nodos distribuidos cuestan 1 SP.

La GUI muestra una fila por atributo y pagina las filas; informa saldo, SP
asignados/tope, nodos/tope y siguiente compra. Verde = activo; amarillo =
desbloqueado; el siguiente comprable es dorado; niveles futuros son oscuros y los
no disponibles, rojos con motivo. Tras cambiar el árbol se reaplican sus efectos
a la mascota invocada.

Costes y valores de los YAML distribuidos:

| Rama común a los cinco tipos | Valores por nivel | Dinero por nivel |
|---|---|---|
| `health` → `max_health` (5) | +2, +4, +6, +8, +10 de vida | 100, 250, 500, 1000, 2000 |
| `regeneration` (5) | Regeneración I, II, III, IV, V | 150, 300, 600, 1200, 2400 |
| `fire_resistance` (1) | Inmunidad al fuego | 1500 |

Los valores son el bonus total del **nivel activo**, no la suma de todos los
niveles anteriores. La regeneración usa el amplificador del nivel de poción;
no significa corazones por segundo.

| Tipo | Primera rama propia: valores / costes | Segunda rama propia: valores / costes | Nodo único (5000; 1 SP) |
|---|---|---|---|
| Perro | `speed`: +5/10/15/20/25% velocidad; 100/250/500/1000/2000 | `scent`: +4/8/12/16/20 bloques; 150/300/600/1200/2400 | `treasure_hunter` |
| Gato | `agility`: +5/10/15/20/25% velocidad; 100/250/500/1000/2000 | `armor`: +1/2/3/4/5 armadura; 150/300/600/1200/2400 | `feline_landing` |
| Loro | `flight_speed`: +5/10/15/20/25% vuelo; 100/250/500/1000/2000 | `alert_range`: +4/8/12/16/20 bloques; 150/300/600/1200/2400 | `lookout` |
| Oso | `strength`: +1/2/3/4/5 daño; 100/250/500/1000/2000 | `speed`: +5/10/15/20/25% velocidad; 150/300/600/1200/2400 | `claw_swipe` |
| Burro | `armor`: +1/2/3/4/5 armadura; 100/250/500/1000/2000 | `knockback_resistance`: +0.1/0.2/0.3/0.4/0.5; 150/300/600/1200/2400 | `courier_saddlebags` |

Las ramas propias tienen cinco niveles; cada habilidad única, uno. Para editar
`skilltrees/<tipo>.yml`, conserva `type`, define por atributo `category`
(`vanilla`/`custom`), `max-level` positivo, `effect`, `money-costs` (un coste
no negativo por nivel) y `values` para vanilla (un número por nivel).
`skill-point-cost` es entero no negativo; alternativamente `skill-point-costs`
define un entero por nivel y tiene prioridad. La GUI admite como máximo seis
niveles por atributo, aunque el cargador acepte más. Un ID de atributo nuevo
necesita las claves `gui.skill-tree.attributes.<id>.name`, `.description` y
`.effect` en `lang/es.yml` para poder mostrarlo. Usa efectos existentes: escribir
un ID custom nuevo no implementa la habilidad y genera un aviso al aplicarlo.

Habilidades automáticas y pasivas:

- **Zarpazo** daña hostiles dentro de una esfera centrada en el oso. No ataca
  jugadores ni mascotas propias; cada intento respeta su cooldown aunque no
  encuentre objetivos. Ajustes y flag: `abilities.zarpazo.*`.
- **Buscatesoros** marca para el dueño menas configuradas y contenedores con
  tabla de botín todavía presente; un cofre normal o un cofre de otro plugin
  sin tabla de botín no se detecta como tesoro. Usa chunks ya cargados, escaneo
  incremental de hasta 1024 inspecciones por tick de habilidad y los dos topes
  configurados. Con Olfato V el radio inicial es 16 + 20 = 36 bloques; el
  presupuesto puede impedir examinar toda la esfera. Muestra un recuento en
  la barra de acción cuando encuentra objetivos. Ajustes: `abilities.buscatesoros.*`.
- **Aterrizaje felino** cancela el daño por caída del dueño mientras tenga un
  gato vivo invocado con el nodo activo; guardarlo o desactivar su flag retira
  la protección. Flag: `abilities.aterrizaje-felino.enabled`.
- **Vigía** busca alrededor del dueño y avisa con sonido y brillo a hostiles
  nuevos respecto al escaneo anterior. No renueva continuamente la marca de
  los mismos enemigos; el brillo ya aplicado expira por sí solo. Con Alcance V
  el radio inicial es 36. Ajustes: `abilities.vigia.*`.
- **Alforjas mensajeras** habilita el almacén y envío del burro; se controla con
  el nodo `courier_saddlebags`, sin flag global propio.

Para resetear, habilita `reset.player-enabled`, abre el árbol y pulsa dos veces
el botón de reinicio (confirmación). Cobra `reset.cost`, borra desbloqueos y
activaciones, devuelve los SP y no devuelve el dinero invertido en nodos. No
existe excepción administrativa por permiso para este botón.

## Muerte, alma y LDPotions

Con muerte habilitada, el daño ambiental puede matar a la mascota; PvP permite
además daño de terceros, nunca del dueño. Al morir queda guardada como muerta y
se entrega un alma al inventario del dueño; si está lleno, cae a sus pies. Las
entregas pendientes se recuperan cuando vuelve a estar disponible el dueño.
Con `death.keep-skills: false` se borra el árbol y se devuelve SP al procesar la
muerte; no hay que esperar a beber la poción.

El alma lleva identidad PDC de token único, dueño y mascota. Su aspecto/nombre
no bastan para crear otra alma válida. Se bloquean transformaciones y uso en
crafteo, yunque, herrería, afiladora, hornos, encantamiento, elaboración de
pociones y otras estaciones, colocación, consumo e interacción con entidades;
se permite guardarla como un objeto normal. Si se copia conservando el token,
solo una copia puede autorizar una resurrección.

Configura la acción de LDPotions para ejecutar **como consola**, al beber:

```text
neopets revive %player%
```

El plugin externo debe sustituir `%player%` por el nombre conectado. El dueño
debe sostener **su alma válida en la mano principal**; puede beber la poción
con la secundaria si LDPotions lo permite. No concedas el permiso de revive a
jugadores solo para beber: lo necesita el emisor del comando. NeoPets valida el
alma y consume una unidad al revivir; si falla la validación, no la consume.
La mascota queda viva y guardada: se invoca de nuevo con `/pets` o `summon`.
El comando no gestiona el consumo ni reembolso de la poción externa.

## Alforjas mensajeras

1. Desbloquea y activa el nodo único de un burro. En `/pets`, Shift+clic derecho
   abre sus 27 espacios persistentes; no es el inventario vanilla del burro.
   Ambos participantes necesitan `neopets.use`.
2. Con cursor vacío, clic izquierdo en una pila para guardar/retirar; clic
   derecho mueve la mitad. Shift+izquierdo y Shift+derecho mueven la pila
   completa. No funcionan arrastrar, teclas numéricas, intercambio de mano ni
   doble clic. Solo hay un visor por alforja y los cambios se guardan antes de
   completar la transferencia.
3. Pulsa **Enviar burro**, elige otro conectado y envía. El burro sale del dueño
   y pasa a tránsito ya durante la solicitud; no puede invocarse mientras tanto.
4. El destinatario pulsa **Aceptar** o **Rechazar** en el chat. Al aceptar aparece
   el burro junto a él y se abre el almacén. Puede cerrarlo y reabrirlo haciendo
   clic derecho sobre el burro recibido; el dueño no puede abrirlo durante el viaje.
5. El destinatario mete/saca objetos y pulsa **Devolver**. Se guarda antes de
   devolverlo. Solo se reinvoca junto al dueño si antes del envío estaba activo.
6. Rechazo, expiración, salida del destinatario o cambio de mundo lo devuelven.
   Si sale el dueño también termina el viaje. El plazo de 300 s empieza al
   solicitar, no al aceptar. La espera entre envíos empieza al solicitar.

Durante el tránsito se cancela todo daño al burro, incluido el de creativo.
Fuera del tránsito puede morir: el contenido se conserva y vuelve a estar
accesible tras revivir. Tras un reinicio, al cargar el dueño se recupera un
estado de tránsito sin solicitud viva. El contenido persiste; solicitudes y
cooldowns se mantienen en memoria y no sobreviven al reinicio.

## GUI administrativa, recarga e idioma

`/neopet admin` expone solo el catálogo `FeatureRegistry`: `death.enabled`,
`death.pvp`, `death.keep-skills`, `rename.enabled`,
`reset.player-enabled`, `reset.cost`, los dos tiempos de `courier`, los flags de
las cuatro habilidades configurables y todos sus números de la tabla anterior.
No edita almacenamiento, rangos, variantes, árboles, idioma, material del alma,
seguimiento ni la lista de menas.

Clic alterna booleanos. En números, izquierdo suma, derecho resta y Shift
multiplica el paso por diez. Los pasos son: reset 100; plazo courier 30 s;
cooldown courier 60 s; radios y daño 0.5; presupuesto de escaneo 512; demás
números 1. Los valores se acotan al rango mostrado; en radios y daño la GUI usa mínimo
0.5, aunque el YAML admite valores positivos inferiores. El cambio se valida, escribe
atómicamente `config.yml` y recarga; si falla la recarga completa se intenta
restaurar el archivo original. No necesitas permiso de reload para editar la GUI.

`/neopet reload` vuelve a validar todos los YAML y publica la configuración solo
si el conjunto es válido. Actualiza mensajes, definiciones, límites y ajustes
consultados dinámicamente. No recrea el almacenamiento, la selección de
integraciones, el cerebro de seguimiento ni el temporizador Bukkit de permisos;
reinicia para cambiar esos elementos. Para comprobar nuevos aspectos o valores
de árboles ya invocados, guarda y vuelve a invocar la mascota.

La versión actual es 16. Las migraciones añaden claves y mensajes nuevos sin
sobrescribir los personalizados; las antiguas apariencias de gatos/loros y los
valores de árboles predeterminados anteriores a v5 tienen migración específica.
No se guarda una migración inválida. No hay copia de seguridad automática.
En `lang/es.yml`, conserva las claves y variables (`<player>`, `<pet>`, etc.);
se valida MiniMessage en modo estricto y no se admiten mensajes vacíos.

## Placeholders e integraciones externas

La expansión PlaceholderAPI se registra automáticamente si está instalado; no
necesita descarga por eCloud. Identificadores exactos en minúsculas:

| Placeholder | Valor |
|---|---|
| `%neopets_sp%` | Saldo SP disponible del jugador |
| `%neopets_sp_max%` | Límite de SP asignados por mascota de su rango |
| `%neopets_pets_owned%` | Total de mascotas poseídas, incluidas muertas |
| `%neopets_pets_active%` | Mascotas de su colección con entidad actualmente registrada |
| `%neopets_max_active%` | Límite de mascotas invocadas por rango |
| `%neopets_active_pet_name%` | Nombre de la primera activa de la colección; sin activa: `-`, editable en `lang/es.yml` → `placeholders.active-pet-empty` |

Sin jugador o con perfil offline/no cargado, los placeholders conocidos devuelven
texto vacío. Un identificador desconocido devuelve `null` a PlaceholderAPI.
Con varias mascotas no se puede elegir una por nombre en el placeholder.

CustomDungeons, LDActivities y kits pueden emitir desde consola, sustituyendo
su propia variable de jugador (la sintaxis exacta depende del plugin externo):

```text
neopet give Ana random dog
neopet give Ana dog_pale,dog_black,dog_snowy
neopet sp give Ana 1
```

No existe dependencia directa de esos plugins ni detección automática de sus
recompensas. Configura cuántas veces se dispara la recompensa en el plugin
externo; cada ejecución válida de `sp give` suma puntos.

## Diagnóstico

| Síntoma / mensaje | Comprobación |
|---|---|
| `NeoPets could not start: …` | Revisa error de YAML/SQL, versión Paper/Java, conexión y permisos de archivos. El plugin se desactiva si no puede arrancar. |
| `NeoPets configuration reload failed: …` / «No se pudo recargar la configuración» | Corrige el archivo y ruta indicados: tipo, rango, clave faltante, aspecto o MiniMessage. La configuración anterior sigue publicada; el YAML manualmente editado sigue necesitando reparación. |
| `Vault is not enabled; economy-backed NeoPets features are disabled` | Instala/habilita Vault y reinicia. |
| `Vault has no registered economy provider; economy-backed NeoPets features are disabled` | Vault solo no aporta dinero: instala un proveedor y reinicia. |
| «Economía no disponible» / nodo rojo | Comprueba Vault/proveedor, dinero, SP, nivel anterior y los dos límites por mascota. |
| `LuckPerms is unavailable; rank limits strategy: Bukkit permissions …` | Se usa el fallback; concede `neopets.rank.<id>` y espera el refresco o reconecta. Los valores vienen de `ranks`. |
| «No tienes permiso» | Concede el nodo concreto de la tabla; `neopets.admin` no agrupa permisos por sí solo. |
| «No se encontró al jugador» / «debe estar conectado» | Revisa nombre; `give`/`sp` requieren historial si está offline, `revive` requiere conexión. |
| «No quedan variantes disponibles» | Toda la selección ya está poseída; prueba otro tipo/lista. |
| «Debes sostener el alma válida…» / «Esta alma no es válida o ya fue utilizada» | Mano principal, PDC original, dueño correcto y token no consumido; no sirve un objeto renombrado. |
| «El burro no está disponible» / «Otra persona está usando las alforjas» | Nodo activo, mascota viva, sin viaje/guardado pendiente; cierra el otro visor y espera el guardado. |
| Buscatesoros no marca un cofre | Debe conservar tabla de botín; comprueba nodo, flag, chunk cargado, radio, presupuesto y cooldown. |
| Vigía no vuelve a avisar del mismo mob | Solo avisa de objetivos nuevos respecto al escaneo anterior; el brillo previo caduca. |
| `No custom pet ability is registered for effect id '…'` | Corrige el `effect` custom a un ID implementado; el YAML no crea código de habilidad. |
| `Could not load NeoPets profile for …` / «No se pudieron cargar tus datos…» | Revisa acceso/latencia SQL y `player-data.load-timeout-seconds`. |
| `Could not complete pet death for …`, `Could not deliver pending souls for …`, `Could not return courier` | Conserva log y copia de BD; revisa la causa SQL antes de repetir operaciones manuales o recrear almas. |
| `NeoPets command failed` / `NeoPets revive command failed` | «Ha ocurrido un error inesperado» es genérico; consulta la excepción completa en consola. |
| Placeholders vacíos/sin resolver | Jugador online con perfil cargado; plugin PlaceholderAPI habilitado al arrancar. El éxito registra `PlaceholderAPI expansion registered`. |

Para validar cambios y detectar regresiones usa el [checklist](live-checklist.md);
no presupone que las pruebas en vivo ya se hayan ejecutado.
