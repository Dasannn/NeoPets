# Checklist de pruebas en vivo

Pruebas manuales pendientes para el servidor de pruebas, basadas en el código
actual. No marcar una casilla sin ejecutar y observar su resultado. Consulta la
[guía de administración](user-guide.md) para valores y limitaciones.

## Preparación y registro

- [ ] **[1 jugador + consola]** Anotar fecha, versión del JAR, Paper, Java,
  motor SQL, plugins de integración y nombres de participantes. En los pasos,
  sustituir `Ana` por el dueño y `Beto` por el segundo jugador. Registrar
  resultado, log y objetos/saldos antes y después de cada fallo.
- [ ] **[Consola]** Ejecutar `./gradlew build` en el worktree antes de desplegar;
  debe terminar correctamente. Instalar el JAR de `neopets-plugin/build/libs/`
  en el servidor detenido; respaldar carpeta `plugins/NeoPets` y BD. Usar un
  área aislada y objetos de prueba contados para los ensayos destructivos.
- [ ] **[Consola]** Arrancar con Paper 26.3 y Java 25: ver
  `NeoPets enabled (version …)`. Comprobar que aparecen `config.yml` (versión 18),
  `lang/es.yml`, cinco YAML de mascotas, cinco árboles y `neopets.db` si SQLite.
  No debe aparecer `NeoPets could not start` ni errores de migración SQL.
- [ ] **[1 jugador + consola, con LuckPerms]** Ejecutar
  `lp user Ana permission set neopets.use true`. Dar a la cuenta de pruebas
  `neopets.admin.give`, `neopets.admin.sp`, `neopets.admin.revive`,
  `neopets.admin.gui` y `neopets.admin.reload` con el mismo formato.
  Preparar suficiente dinero con el proveedor de economía instalado; su comando
  depende del proveedor y no forma parte de NeoPets. Anotar el saldo real.
- [ ] **[2 jugadores + consola]** Ejecutar
  `lp user Beto permission set neopets.use true`; mantener ambas cuentas
  conectadas para las secciones marcadas **[2 jugadores]**.

## Instalación, dependencias y almacenamiento

- [ ] **[1 jugador, SQLite]** Entrar, recibir una mascota y SP con
  `neopet give Ana dog_pale` y `neopet sp give Ana 3` desde consola. Salir,
  reiniciar de forma normal y volver: colección y saldo permanecen.
- [ ] **[1 jugador, MySQL/MariaDB]** Con respaldo y servidor detenido, usar una
  base de pruebas separada y configurar `storage.type: mysql` y sus cinco datos
  de conexión. Arrancar: migraciones correctas; repetir entrega, SP y reinicio.
  No esperar que aparezcan los datos de SQLite: no hay copia entre motores.
- [ ] **[1 jugador]** Con el servidor detenido, probar sin Vault y luego con
  Vault sin proveedor. Deben aparecer respectivamente `Vault is not enabled`
  y `Vault has no registered economy provider`, con el sufijo
  `economy-backed NeoPets features are disabled`. `/pets` y SP funcionan;
  comprar nodos muestra economía no disponible. Restaurar y reiniciar.
- [ ] **[1 jugador]** Arrancar sin LuckPerms: aviso
  `LuckPerms is unavailable; rank limits strategy: Bukkit permissions …`;
  aplicar permisos con el gestor Bukkit disponible, esperar el intervalo y
  comprobar límites. Si no se dispone de gestor alternativo, anotar pendiente.
- [ ] **[1 jugador]** Arrancar sin PlaceholderAPI: NeoPets carga; no hay expansión.
  Restaurarlo y reiniciar: aparece `PlaceholderAPI expansion registered`.
- [ ] **[1 jugador]** En una copia de pruebas, simular credenciales SQL erróneas:
  el arranque falla con `NeoPets could not start: …`. Restaurar credenciales;
  el siguiente arranque carga normalmente. No modificar una BD compartida.

## Comandos, entrega y permisos

- [ ] **[1 jugador sin OP]** Sin permisos concedidos manualmente, ejecutar
  `/neopets` y `/pets`: se abre el menú. Los comandos administrativos siguen
  denegados. Conceder `neopets.admin` y comprobar los seis subpermisos.


- [ ] **[1 jugador]** Sin `neopets.use`, ejecutar `/pets` y
  `/neopet summon dog_pale`: «No tienes permiso». Restaurarlo y abrir
  `/neopets` y `/pets`: ambos muestran «Mis mascotas». En consola, `neopets`
  responde que solo puede usarlo un jugador si supera el control de permiso.
- [ ] **[1 jugador]** Conceder solo `neopets.admin` a una cuenta sin los nodos
  concretos; `/neopet admin` y `/neopet reload` deben seguir denegados salvo
  herencias externas del gestor. Comprobar que los nodos concretos sí habilitan
  cada acción. `neopets.admin` sí hereda `neopets.admin.reset`; comprobar reset
  permitido por ese padre y denegado sin ninguno de los dos nodos.
- [ ] **[1 jugador + consola]** `neopet give Ana dog_pale` dos veces: solo una
  variante; la segunda responde que ya la posee. `neopet give Ana no_existe` y
  `neopet give Ana random no_existe` informan variante/tipo desconocido.
- [ ] **[1 jugador + consola]** `neopet give Ana dog_black,dog_snowy` tres veces:
  se entregan como máximo las dos variantes distintas; después «No quedan
  variantes disponibles». Con comando vacío no se ejecuta compensación ni se crea otra instancia.
- [ ] **[1 jugador + consola]** Configurar `give.all-owned-command: "say premio <player> <uuid>"`
  y recargar. Agotar una lista/random y repetir: un solo anuncio con nombre/UUID
  sustituidos además del mensaje. Repetir con ID específico ya poseído. Una
  variante nueva o ID/tipo inválido no anuncia; restaurar `""` y comprobar que no anuncia.
- [ ] **[1 jugador + consola]** Repetir `neopet give Ana random parrot` hasta
  agotar los cinco loros: nunca otro tipo ni repetidos. `neopet give Ana random`
  elige entre todas las variantes todavía no poseídas.
- [ ] **[1 jugador + consola]** Para una selección con pesos 10 y 2, comprobar
  YAML y ejecutar entregas a perfiles de prueba sin esa selección: solo salen
  candidatos válidos. Registrar frecuencias si se repite con muchos perfiles;
  una muestra pequeña no garantiza una proporción exacta.
- [ ] **[1 jugador + consola]** `neopet give NuncaConectado dog_pale` debe dar
  jugador no encontrado (usar un nombre que realmente no tenga historial).
  Desconectar Ana y ejecutar `neopet give Ana cat_tabby` y
  `neopet sp give Ana 2`: funcionan para su perfil conocido; al entrar aparecen.
- [ ] **[1 jugador + consola]** `neopet sp set Ana 5`, `neopet sp take Ana 2`,
  `neopet sp give Ana 1`: saldos 5, 3 y 4. `neopet sp take Ana 5` se rechaza sin
  modificar el saldo. `neopet sp set Ana 0` funciona; `give … 0`, `take … -1`,
  `give … abc` y `give … 2147483648` se rechazan. En perfil de prueba,
  `neopet sp set Ana 2147483647` seguido de `neopet sp give Ana 1` rechaza el
  desbordamiento; restaurar con `neopet sp set Ana 10`.
- [ ] **[1 jugador + consola]** `neopet reset Ana` y `neopet revive Ana` solo
  muestran el uso general: no son subcomandos registrados. `neopets revive Ana`
  sí alcanza la validación del alma.

## GUI, variantes y seguimiento

- [ ] **[1 jugador]** Dar con `neopet give Ana <id>` una variante de cada aspecto
  de la tabla de la guía. Invocar sucesivamente con `/neopet summon <id>` y
  guardar con `/neopet dismiss <id>`: aspecto correcto para los nueve lobos,
  once gatos, cinco loros, oso y burro. Probar todas las crías distribuidas:
  permanecen pequeñas; no se espera un loro bebé.
- [ ] **[1 jugador]** En `/pets`, izquierdo invoca/guarda y añade/quita brillo;
  derecho abre el árbol. Shift+izquierdo abre renombrado por chat. Escribir
  `Compañero`, comprobar nombre en menú/mundo y persistencia tras reconectar.
  Repetir y escribir `cancelar`: conserva nombre. Probar vacío y más de 32
  caracteres visibles: rechazo. Desactivar `rename.enabled` desde admin:
  Shift+clic ya no inicia renombrado; restaurar.
- [ ] **[1 jugador]** Entregar suficientes variantes con `neopet give Ana random`
  hasta superar 28. Comprobar paginación, filtro por tipo, cabecera con colección,
  activas y SP, ayuda/cerrar/volver. No debe poder robar iconos con Shift,
  arrastre, números, doble clic o intercambio de mano.
- [ ] **[1 jugador]** Con `ranks.default.max-active-pets: 1`, invocar dos variantes:
  la segunda falla por límite. `/neopet dismiss dog_pale` sobre una guardada
  informa que ya lo está. Invocar con UUID de mascota y con ID de variante
  debe seleccionar la misma instancia. Un ID ajeno devuelve «No posees…».
- [ ] **[1 jugador]** Caminar, correr, cruzar obstáculos y alejarse más de 20
  bloques: sigue/teleporta sin generar copias. Cambiar de mundo y reconectar:
  se restaura una sola entidad de cada activa. Reiniciar y cargar chunks viejos:
  no quedan entidades huérfanas duplicadas.
- [ ] **[2 jugadores]** Beto intenta montar, alimentar para criar/domesticar,
  usar correa y abrir inventario vanilla de las mascotas de Ana: se bloquea
  la interacción protegida. Ana no puede dañar su propia mascota, tampoco
  con proyectil atribuido al dueño.

## Rangos y árbol de habilidades

- [ ] **[1 jugador]** Verificar nombre, descripción, efecto e icono del tercer
  nodo: perro Piel dura/SHIELD (+Resistencia I); gato Patas acolchadas/HAY_BLOCK
  (sin daño por caída de la mascota); loro Plumaje ignífugo/MAGMA_CREAM;
  oso Nadador polar/TURTLE_HELMET (respiración acuática); burro Pisada firme/
  SOUL_SAND (sin ralentización por arena de almas, miel o nieve en polvo).
  Cada nodo cuesta 1500 y 1 SP. Activar y desactivar para comprobar el efecto.
- [ ] **[Consola + jugador]** Actualizar una instalación antigua (anterior a v17) a v18 con progreso activo
  y desbloqueado en `fire_resistance`: perro/gato/oso/burro conservan niveles y SP
  bajo el nuevo ID, incluso tras reiniciar. El loro conserva su ID. Repetir con
  un árbol personalizado que retenga el ID antiguo: no debe remapearse.


- [ ] **[1 jugador]** Definir temporalmente en config `ranks.vip` con límites
  2/6/16, ejecutar `/neopet reload` y desde consola
  `lp user Ana permission set neopets.rank.vip true`: puede invocar dos mascotas
  y la GUI muestra 6 SP/16 nodos por mascota. Quitar con
  `lp user Ana permission unset neopets.rank.vip`; los límites consultados vuelven
  a default. Probar también un grupo LuckPerms llamado `vip` si existe; no
  crear/modificar rangos compartidos sin coordinarlo.
- [ ] **[1 jugador]** Con dos rangos de prueba de límites cruzados (por ejemplo
  2/3/10 y 1/6/16), asignar ambos: límites 2/6/16, el máximo por campo. Metadatos
  numéricos de LuckPerms sin rango/permisos coincidentes no cambian los límites.
- [ ] **[1 jugador]** Default 1/3/10, dinero suficiente y
  `neopet sp set Ana 10`: abrir árbol de perro con clic derecho. Intentar
  Vida II antes de Vida I: bloqueado. Comprar Vida I: cobra 100, sin consumir
  SP ni aplicar vida todavía. Segundo clic activa por 1 SP y da +2 de vida.
  Comprar Vida II cuesta 250; activarlo deja +4 total, no +6.
- [ ] **[1 jugador]** Desbloquear varios niveles pero intentar activar II de
  otra rama antes de I: exige activar el anterior. Gastar 3 SP en una mascota:
  la cuarta activación se bloquea por rango aunque haya saldo. Otra mascota
  tiene su propio límite de 3 y comparte el mismo saldo disponible.
- [ ] **[1 jugador]** Comprar diez nodos sin activarlos todos: el undécimo se
  rechaza por límite y no cobra. Sin dinero o SP, comprobar nodo rojo con motivo;
  activo verde, desbloqueado amarillo, próximo comprable dorado, futuros oscuros.
  Cada fila es una rama, con navegación a las demás filas.
- [ ] **[1 jugador]** Para cada tipo, comprar/activar sucesivamente Vida,
  Regeneración, el tercer nodo propio de su tipo y sus dos ramas propias. Usar un rango de pruebas
  1/22/22 si hace falta y `neopet sp set Ana 100`. Medir atributos antes/después
  con `/attribute <UUID-entidad> minecraft:max_health get`,
  `/attribute <UUID-entidad> minecraft:movement_speed get` u otro atributo
  correspondiente. Comparar con los valores de la guía; guardar/reinvocar
  no acumula el bonus dos veces. Los UUID de entidad se obtienen apuntando con
  la pantalla de depuración; no confundirlos con el UUID persistente de mascota.
- [ ] **[1 jugador]** Con `reset.player-enabled: false`, no hay reset operativo.
  Habilitarlo y `reset.cost: 5000` en admin, abrir árbol y pulsar reset una vez:
  solo solicita confirmación, sin cobrar. Confirmar: cobra 5000 una vez,
  devuelve exactamente SP asignados, borra desbloqueos y efectos, sin devolver
  las compras previas. Repetir sin dinero: no cambia árbol/saldo. Probar coste
  cero y restaurar valores.

- [ ] **[1 jugador + consola]** Activar 3 SP en una mascota y anotar saldo/dinero.
  Con reset de jugador desactivado, ejecutar `neopet reset Ana dog_pale`: saldo +3,
  árbol sin desbloqueos ni efectos, dinero sin cambios. Repetir: +0 SP. Probar UUID
  persistente y nombre personalizado con espacios; tab sugiere conectados y solo
  mascotas del jugador seleccionado. Nombre desconocido y UUID inexistente: error;
  UUID de mascota de otro jugador: no propietario. Ana desconectada: error, sin cambios.
  Sin `neopets.admin.reset`: denegado; con permiso, consola y jugador pueden ejecutar.

## Habilidades únicas

Para cada ensayo, entregar el tipo con `neopet give Ana <id>`, abrir su árbol,
comprar y activar el nodo único y luego invocarlo. Guardar la mascota anterior
para respetar el límite. Las habilidades de área son automáticas; no tienen
comando de activación.

- [ ] **[1 jugador] Zarpazo:** `neopet give Ana polar_bear`; en un área controlada,
  `/summon minecraft:zombie ~3 ~ ~` con el oso cerca. Dentro de 6 bloques recibe
  daño 4 por intento, con partículas/sonido y cooldown 10 s; fuera no.
  Desactivar `abilities.zarpazo.enabled` con admin: deja de atacar en área;
  reactivar. Animales neutrales y otras mascotas propias no reciben el zarpazo.
- [ ] **[2 jugadores] Zarpazo:** Beto junto al oso no recibe daño del área;
  confirmar lo mismo con una mascota propia de Ana cerca de los hostiles.
- [ ] **[1 jugador] Buscatesoros:** `neopet give Ana dog_pale`; colocar cerca una
  mena con `/setblock ~3 ~ ~ minecraft:diamond_ore` y un cofre normal con
  `/setblock ~4 ~ ~ minecraft:chest`. Crear un cofre de botín sin abrir con
  `/setblock ~5 ~ ~ minecraft:chest{LootTable:"minecraft:chests/simple_dungeon"}`.
  Tras la búsqueda marca mena/cofre con tabla durante 5 s y da recuento en la
  barra de acción; no marca el cofre normal. Abrir el de botín y volver a
  probar: ya no debe detectarlo por la tabla consumida. Repetir con un cofre
  real de CustomDungeons: solo cuenta si conserva una tabla de botín vanilla.
- [ ] **[2 jugadores] Buscatesoros:** Ana ve partículas; Beto no debe recibir las
  partículas privadas de esa búsqueda. Desactivar el flag: deja de escanear;
  reactivar y esperar cooldown. Reducir `max-scan-blocks` y colocar menas a varias
  distancias: se inspecciona primero lo cercano y se recorta lo más lejano; no carga
  chunks para ampliar cobertura. Restaurar presupuesto. Olfato V amplía radio base 16 a 36, con los topes
  de escaneo todavía vigentes; no esperar que recorra toda la esfera.
- [ ] **[1 jugador] Aterrizaje felino:** `neopet give Ana cat_tabby`; con nodo
  activo y gato invocado, saltar desde altura segura de pruebas: sin daño de
  caída. Guardar gato y repetir: daño normal. Reinvocar y desactivar
  `abilities.aterrizaje-felino.enabled`: daño normal; restaurar.
- [ ] **[2 jugadores] Vigía:** `neopet give Ana parrot_red_blue`; invocar zombie
  nuevo a menos de 16 bloques de Ana: tras el escaneo, Ana oye aviso y ambos
  ven brillo. Expira a los 5 s; no se renueva si sigue siendo el mismo objetivo
  del escaneo anterior. Sacarlo del radio durante un escaneo y devolverlo:
  avisa de nuevo. Alcance V suma 20 bloques; desactivar flag elimina nuevos
  avisos y las marcas existentes caducan solas.

## Muerte, PvP, alma y resurrección

- [ ] **[1 jugador]** Con `death.enabled: true`, `death.keep-skills: true`,
  inventario con hueco y mascota invocada, infligir daño ambiental letal en el
  área de prueba (o consola `damage <UUID-entidad> 100 minecraft:generic`).
  Muere una vez, no deja botín vanilla de mascota, aparece gris/roja en `/pets`
  y entrega una sola alma identificada. `/neopet summon <variante>` se rechaza.
- [ ] **[1 jugador]** Repetir con inventario lleno: el mensaje indica que el alma
  cayó a los pies del dueño; comprobar posición y recogerla sin duplicación.
- [ ] **[1 jugador]** Con `death.enabled: false`, repetir daño letal: se cancela, queda
  con 1 de vida y no se emite alma; un daño no letal puede reducir vida. Restaurar `true`.
  No usar `/kill` como sustituto de daño normal para probar invulnerabilidad.
- [ ] **[2 jugadores]** `death.pvp: false`: Beto golpea/dispara a la mascota de
  Ana, sin daño. Activar desde admin: Beto sí puede dañarla/matarla; Ana sigue
  sin poder dañar la suya. Restaurar `false`.
- [ ] **[1 jugador + consola]** Con alma en secundaria o inventario pero no
  principal, `neopets revive Ana` falla y conserva alma. Con objeto normal
  renombrado «Alma…» también falla. Con alma válida en principal, el mismo
  comando consume una unidad y revive como guardada; invocar desde `/pets`.
  El árbol conservado vuelve a aplicar efectos.
- [ ] **[2 jugadores + consola]** Pasar alma de Ana a Beto y ejecutar
  `neopets revive Beto`: alma de otro dueño, no resucita ni la consume. Con
  Ana desconectada, `neopets revive Ana` informa que debe estar conectada.
- [ ] **[1 jugador]** Con `death.keep-skills: false`, activar mejoras y registrar
  saldo; matar mascota: árbol se borra y SP se devuelven **al procesar muerte**,
  dinero no. Revivir: no duplica el reembolso. Restaurar `true`.
- [ ] **[1 jugador + LDPotions]** Configurar la poción para ejecutar como consola
  `neopets revive %player%`. Alma principal y poción secundaria: beber y ver
  una resurrección, consumo único del alma, mascota guardada. Probar sin alma:
  NeoPets rechaza; anotar aparte si LDPotions consume igualmente la poción.
  La sustitución de `%player%` corresponde a LDPotions.

## Antiabuso del alma

Usar exclusivamente copias de prueba en este servidor; cada intento debe
conservar el token intacto o cancelar la acción sin generar un resultado nuevo.
Para estaciones incompatibles con ECHO_SHARD, repetir con el material de alma
apropiado configurado **antes de emitir una nueva alma** (por ejemplo STONE,
IRON_SWORD, COAL o POTION) y restaurar al terminar.

- [ ] **[1 jugador]** Alma en yunque: intentar renombrar y combinar por cursor,
  Shift+clic, arrastre, tecla numérica y mano secundaria. Entrada/resultado
  bloqueados; no hay alma transformada. Repetir en herrería y afiladora.
- [ ] **[1 jugador]** Probar crafteo 2×2 y mesa 3×3, crafter automático y
  encantamiento: no permiten transformar/consumir el alma. Incluir intento de
  receta válida para el material de prueba, no solo una receta imposible.
- [ ] **[1 jugador]** Intentar insertar alma como entrada/combustible de horno,
  ahumador, alto horno y soporte de pociones, también con tolva: se bloquea.
  Probar cortapiedras, telar, cartografía y depósitos especiales compatibles.
- [ ] **[1 jugador]** Con alma de material colocable/consumible, intentar colocar,
  consumir, dispensar, equipar en soporte de armadura y usar sobre entidades
  o marco: se bloquea. Mover a cofre normal y recoger sí debe funcionar.
- [ ] **[1 jugador + consola]** Duplicar un alma de prueba conservando todo su
  PDC (copia de creativo), guardar una copia y revivir con la primera mediante
  `neopets revive Ana`. Intentar la segunda inmediatamente: rechazo. Volver a
  matar la mascota, sostener la copia antigua y repetir: también rechazo,
  aunque ahora esté muerta. Solo el token nuevo puede revivir esa muerte.
- [ ] **[1 jugador + consola]** Con dos copias válidas del mismo token en la mano,
  emitir `neopets revive Ana` dos veces seguidas: como máximo una resurrección,
  sin consumo doble autorizado ni reembolso SP duplicado. Reconectar/reiniciar
  y repetir con token consumido: sigue inválido.

## Alforjas mensajeras y antidupe

Preparar `neopet give Ana donkey`; comprar y activar `courier_saddlebags` por
5000 y 1 SP. Ana y Beto tienen `neopets.use`. Para repetir sin esperar 15 minutos,
poner temporalmente `courier.cooldown-seconds: 0` y para expiración un plazo
breve, por ejemplo 15 s; usar admin o editar y `/neopet reload`. Registrar cada
pila y su total entre inventarios y alforjas.

- [ ] **[1 jugador]** `/pets`, Shift+derecho en burro: 27 espacios. Depositar 32
  piedras con cursor vacío; retirar con derecho y comprobar media pila.
  Cerrar, reabrir, reconectar y reiniciar: contenido exacto. Sin nodo activo o
  con burro muerto, no se abre. Inventario vanilla del burro no es accesible.
- [ ] **[2 jugadores]** Pulsar Enviar y seleccionar Beto: solicitud con botones,
  burro en tránsito y `/neopet summon donkey` rechazado. Beto acepta por botón
  (ejecuta `/neopet courier accept <UUID-solicitud>`): burro junto a él y GUI
  abierta. Depositar/retirar, cerrar, reabrir con derecho sobre el burro y
  pulsar Devolver: contenido vuelve intacto a Ana. Si originalmente estaba
  invocado vuelve invocado; repetir con burro guardado y comprobar que queda
  guardado al devolver.
- [ ] **[2 jugadores]** Rechazar con botón
  (`/neopet courier reject <UUID-solicitud>`): vuelve sin pérdida. Repetir
  accept/reject con el mismo UUID ya finalizado: no recrea viaje ni contenido.
  Que Ana intente responder al UUID destinado a Beto: rechazo.
- [ ] **[2 jugadores]** Dejar solicitud sin aceptar 15 s: vuelve sola. Enviar,
  aceptar a los 10 s y esperar hasta el plazo original: vuelve cerca de los
  15 s desde envío, no 15 s desde aceptación. Restaurar plazo a 300 s.
- [ ] **[2 jugadores]** Con cooldown 900 s, enviar y rechazar; otro intento
  inmediato informa espera. No hay nuevo envío ni segunda entidad.
- [ ] **[2 jugadores] Dos visores:** mientras Ana tiene alforjas abiertas, Beto
  intenta abrir el burro: no obtiene acceso. Enviar/aceptar y hacer que Ana
  intente abrir desde `/pets` mientras Beto lo usa: no puede. Dos aperturas
  rápidas de Beto tampoco crean sesiones independientes; revisar total final.
- [ ] **[2 jugadores] Transferencias:** en GUI recibida, probar Shift+izquierdo y
  Shift+derecho: ambos mueven pila completa; derecho sin Shift mueve mitad.
  Arrastrar, números, doble clic, intercambio de mano y cursor ocupado
  se bloquean. Spamear clics normales y cerrar durante
  guardado: no multiplica objetos. Contar total antes/después.
- [ ] **[2 jugadores] Desconexión durante transferencia:** Beto mueve una pila
  y sale inmediatamente. Tras guardado vuelve al dueño; al reconectar Beto,
  sumar ambos inventarios y alforjas: total inicial, sin copia ni pérdida.
  Repetir saliendo Ana mientras Beto mueve objetos: se cierra/guarda, finaliza
  viaje y no queda accesible una GUI antigua.
- [ ] **[2 jugadores] Cambio de mundo:** Beto acepta y cambia de mundo durante
  la entrega: el viaje vuelve con contenido guardado, sin otro burro perdido.
- [ ] **[2 jugadores] Daño durante tránsito:** intentar daño ambiental, golpes y
  golpes de creativo de Beto al burro aceptado: se cancelan; no muere, no emite
  alma ni permite duplicar contenido con devolución simultánea. Intentar
  durante solicitud: ya no hay entidad junto al dueño que pueda morir.
- [ ] **[2 jugadores] Muerte de participante durante tránsito:** Beto muere con
  alforjas abiertas; revisar cierre/guardado y contar objetos entre drops,
  inventarios y alforjas. La muerte por sí sola no tiene devolución especial:
  finalizar con Devolver, desconexión o expiración. Repetir con muerte de Ana:
  no se duplica contenido ni se autoriza un segundo visor.
- [ ] **[1 jugador] Muerte fuera de tránsito:** cargar alforjas, matar burro
  con muerte habilitada, revivir con alma principal y `neopets revive Ana`:
  27 espacios conservados. Si se usó `keep-skills: false`, volver a comprar y
  activar el nodo antes de abrir.
- [ ] **[2 jugadores] Reinicio:** reiniciar normalmente con entrega aceptada;
  al volver Ana no queda bloqueada en tránsito y el contenido sigue una sola
  vez. El cooldown no persiste tras reiniciar. No marcar como prueba de caída
  abrupta: apagar limpiamente no demuestra recuperación ante corte del proceso.

## Admin, migración y recarga

- [ ] **[1 jugador]** `/neopet admin`: solo los ajustes del catálogo de la guía,
  sin contraseñas SQL ni rangos. Alternar muerte, PvP, conservar habilidades,
  renombrado y reset: archivo y conducta cambian. Flag tienda se guarda, pero
  no aparece compra implementada. No hay flag global de Alforjas.
- [ ] **[1 jugador]** Probar números: izquierdo suma, derecho resta, Shift ×10;
  verificar pasos de reset 100, courier 30/60 s, radios/daño 0.5, escaneo 512 y
  demás 1. En mínimo/máximo se acota; no guarda valores fuera de rango. Reabrir
  y reiniciar: valores persistidos. Restaurar predeterminados tras ensayos.
- [ ] **[1 jugador + consola]** Editar un texto visible de `lang/es.yml`, un peso
  y una variante válida; `/neopet reload` muestra «Configuración … recargada».
  Reabrir GUI y probar entrega de la nueva variante. No es necesario reiniciar
  para esos datos. Guardar/reinvocar para comprobar un aspecto cambiado.
- [ ] **[1 jugador + consola]** Con respaldo, poner temporalmente radio 129,
  tipo booleano incorrecto, aspecto inválido o cerrar mal una etiqueta de
  MiniMessage; tras cada caso ejecutar `/neopet reload`: error claro con
  archivo/ruta y configuración anterior todavía funcionando. Restaurar YAML
  válido y recargar: éxito. Probar también `config-version: 19`: se rechaza.
- [ ] **[1 jugador + consola]** En copia de datos antigua de versión conocida
  (1–17), conservar valores personalizados y arrancar/recargar: avanza a 18,
  añade claves/mensajes faltantes y preserva los personalizados. Desde v15 añade
  `give.all-owned-command: ""` y mensajes de reset; conserva un comando personalizado
  si ya existía. Un valor booleano/lista en esa clave se rechaza al recargar. Para origen
  anterior a v4, comprobar gatos `appearance.type` y loros `red`/`cyan`; anterior
  a v5, solo las listas antiguas predeterminadas se convierten en bonus.
  No bajar la versión de archivos actuales como sustituto de una copia antigua.
- [ ] **[1 jugador + consola]** Una migración con otro YAML inválido no debe
  guardar parcialmente los documentos migrados. Reparar y repetir; entonces
  se guarda la versión 18. Comprobar respaldo manual: el plugin no crea uno.
- [ ] **[1 jugador]** Cambiar `follow.teleport-distance` y refresco Bukkit:
  `/neopet reload` valida pero los objetos/timers existentes mantienen el valor
  de arranque. Reiniciar y comprobar nuevo comportamiento. Cambios de motor
  SQL y presencia de dependencias también se prueban con reinicio.
- [ ] **[1 jugador]** Forzar error de recarga completa al editar desde GUI
  (por ejemplo otro YAML inválido, con respaldo): «El cambio no es válido y no
  se guardó», valor activo sin cambios y `config.yml` restaurado si el disco
  permite la operación. Corregir y volver a probar edición válida.

## PlaceholderAPI y recompensas externas

- [ ] **[1 jugador + PlaceholderAPI]** Ejecutar
  `/papi parse me %neopets_sp%`, `/papi parse me %neopets_sp_max%`,
  `/papi parse me %neopets_pets_owned%`, `/papi parse me %neopets_pets_active%`,
  `/papi parse me %neopets_max_active%` y
  `/papi parse me %neopets_active_pet_name%`: coinciden con saldo, colección,
  entidades activas y límites. Guardar todas: nombre `-`. Renombrar e invocar:
  devuelve el nombre nuevo. Con varias activas devuelve la primera de la
  colección, no un nombre seleccionable mediante parámetros.
- [ ] **[1 jugador + PlaceholderAPI]** Cambiar
  `placeholders.active-pet-empty` en `lang/es.yml` y `/neopet reload`: nuevo
  texto sin mascota activa. Las consultas conocidas sin perfil online cargado
  devuelven vacío; un identificador desconocido no es un placeholder válido.
- [ ] **[1 jugador + CustomDungeons/LDActivities]** En recompensa de prueba,
  configurar como consola `neopet sp give Ana 1` (después sustituir Ana por la
  variable real del plugin externo). Abrir/completar una vez: saldo +1.
  Configurar `neopet give Ana dog_pale,dog_black`: una entrega sin repetición.
  Registrar si el sistema externo dispara más de una vez; NeoPets suma cada
  ejecución válida de SP, no deduplica recompensas del plugin externo.

## Rendimiento y cierre

- [ ] **[1 jugador] Buscatesoros máximo:** con rango temporal 1/22/22, activar
  Olfato V y Buscatesoros; radio inicial efectivo 36. Preparar menas y varios
  contenedores en chunks ya cargados. Observar con `/mspt` y `/tps` antes y
  durante varios cooldowns: sin congelaciones, sin carga deliberada de chunks
  para escanear, como máximo `max-targets` y presupuesto `max-scan-blocks` por
  activación. Registrar cifras; el código no garantiza un umbral de MSPT para
  cualquier hardware. Probar el límite configurado de escaneo en entorno
  controlado, no asumir que radio máximo implica cobertura completa.
- [ ] **[2 jugadores, ampliar participantes si están disponibles] Muchas mascotas:**
  rango temporal con `max-active-pets` mayor, entregar variantes distintas con
  `neopet give Ana random` y a Beto; invocar varias con habilidades activas.
  Medir `/mspt` y `/tps`, observar seguimiento, colisiones, partículas y spam de
  logs durante movimiento y reconexiones. No acumular entidades al guardar,
  salir o reiniciar; registrar cantidad de mascotas y parámetros de escaneo.
- [ ] **[Consola]** Revisar log completo: sin errores repetidos de guardado,
  tokens, devolución, carga ni reaplicación de efectos. Al apagar, no debe
  aparecer `NeoPets shutdown did not complete cleanly`.
- [ ] **[1–2 jugadores + consola]** Restaurar YAML, permisos/rangos, saldos y
  materiales de prueba; eliminar entidades/objetos de ensayo del área.
  Recargar o reiniciar según corresponda. Anotar pruebas pendientes, fallos
  reproducibles y evidencia; estas casillas no sustituyen aprobación del dueño.

## Permisos sin OP y actualizaciones firmadas

- [ ] OP sin nodos NeoPets: give/sp/revive/reset/admin/reload/update denegados y
  sin sugerencias administrativas. Jugador no OP con hijo explícito: acción permitida.
  Con nodo global: todos los hijos permitidos. `/pets` conserva `neopets.use` por defecto.
- [ ] Consola local y RCON sin nodos: acceso admin; LDPotions puede ejecutar
  `neopets revive Ana` desde consola con alma válida en la mano principal.
- [ ] Migrar v17 a v18: conserva ajustes, añade updater y mensajes. Rechazar
  booleanos incorrectos, repositorio con URL/ruta inyectada y token no textual;
  ningún diagnóstico muestra el token. Verificar precedencia entorno > config >
  incluido > ninguno; entorno explícitamente vacío produce NO_TOKEN.
- [ ] JAR local/CI sin recurso de token: config vacía y entorno ausente producen
  NO_TOKEN. Release con token incluido y config vacía: check funciona sin editar
  config; ni errores ni título/notas que repitan la credencial la muestran.
- [ ] `/neopets update check`: muestra instalada/última, título y primera línea;
  probar estable, prerelease habilitada/deshabilitada y SNAPSHOT.
- [ ] Con clave PLACEHOLDER o ausente se rechaza instalar. Sin token, token sin
  acceso o caducado: mensaje específico sin credenciales. Probar límite de API.
- [ ] Release firmada con clave correcta: update prepara exactamente el nombre
  del JAR actual en la carpeta update; reiniciar y comprobar versión nueva.
- [ ] Assets ausentes, SHA incorrecto, firma inválida/JAR alterado, versión igual
  o inferior: no crean ni reemplazan el JAR preparado. Dos llamadas simultáneas:
  segunda rechazada. Descarga lenta no bloquea ticks; respuestas llegan en hilo principal.
- [ ] JAR antiguo firmado con clave correcta y renombrado como una release nueva:
  se rechaza por versión distinta en `paper-plugin.yml` y conserva el JAR preparado.
  Repetir sin descriptor o con versión ausente/inválida.
- [ ] Workflow sin secret de firma: falla. Sin `NEOPETS_UPDATER_TOKEN`: publica
  tres assets y aviso, sin token incluido. Con ambos secrets y tag v*: tres assets,
  versión correcta, firma/checksum válidos tras inyección y ningún token/clave en
  logs, fuentes ni cachés Gradle. Instalar manualmente una primera release oficial.
- [ ] Revocar token en GitHub, actualizar secret y publicar release: token antiguo
  falla sin exponerlo; instalar nueva release manualmente o mediante credenciales
  propias y comprobar autenticación con el token nuevo.
