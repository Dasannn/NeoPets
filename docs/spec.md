# NeoPets — Especificación funcional (MVP)

> Estado: **APROBADA** — revisión 3 (2026-10-23)
> Depende de: `docs/constitution.md`

## 1. Alcance del MVP

Incluido:
- Sistema de mascotas: posesión, invocar/desinvocar, GUI de selección (mascota + skin/variante), muerte y revivir.
- Comandos de entrega (aleatoria y específica), sin repetir variantes ya poseídas.
- Árbol de atributos (skill tree) por tipo de mascota, independiente del sistema de mascotas.
- Límites por rango configurables (LuckPerms). Hoy solo existe el rango por defecto; los rangos VIP quedan preparados en config.
- GUI de administración de features vinculada a `config.yml`.
- Integraciones: Vault, LuckPerms, PlaceholderAPI, CustomDungeons/LDActivities (vía comandos), LDPotions (vía comando).

Fuera del MVP (previsto en el diseño, no implementado):
- Pirámide/tier list de rarezas.
- Skills exclusivas VIP.
- Compra de mascotas activada por defecto (existe, pero `false`).

## 2. Glosario

| Término | Significado |
|---|---|
| **Tipo** | Especie base: `dog`, `cat`, `parrot`, `polar_bear`, `donkey`. |
| **Variante** | Tipo + aspecto vanilla (raza/color/patrón) + flag bebé. Cada variante es una mascota distinta con ID configurable, p. ej. `parrot_red`, `bird01`. |
| **Mascota (instancia)** | Variante que posee un jugador, con su estado (viva/muerta, nombre, progreso del árbol). |
| **Atributo** | Rama del skill tree de un tipo (p. ej. Vida). Tiene N nodos (niveles). |
| **Nodo** | Un nivel de un atributo. Estados: *bloqueado* → *desbloqueado* (pagado con dinero) → *activo* (SP asignado). |
| **SP (skill point)** | Punto por jugador; se obtiene en dungeons/actividades y se asigna a nodos desbloqueados. |

## 3. Sistema de mascotas

### 3.1 Tipos y variantes
- 5 tipos en el MVP: perro, gato, loro, oso polar, burro.
- Variantes definidas en `pets/<tipo>.yml`: ID, aspecto vanilla, `baby: true|false` (bebé permanente), nombre visible e ítem de icono.
- Cada variante tiene un `weight` (peso) para la entrega aleatoria.

### 3.2 Posesión e invocación
- Posesión ilimitada, pero **nunca dos veces la misma variante**.
- `max-active-pets` por rango limita cuántas mascotas puede tener **invocadas a la vez**. Por defecto 1; borrador VIP: 2.
- Las mascotas se invocan y desinvocan desde la GUI o por comando.
- La mascota es permanente salvo que muera (si la muerte está activada).

### 3.3 GUI "Mis mascotas"
- `/neopets` (alias `/pets`) abre un menú de cofre paginado con las variantes poseídas.
- Clic: invocar/desinvocar. Clic derecho: abrir su skill tree. Shift-clic: renombrar (si está activado).
- Mascotas muertas se muestran en gris con indicación de cómo revivirlas.

### 3.4 Comportamiento en el mundo
- Sigue al dueño, se teletransporta si se aleja más de X bloques (configurable), no se puede montar/robar/criar/domesticar por terceros, no despawnea.
- Se desinvoca al desconectarse el dueño y se reinvoca al conectarse si estaba activa.
- Las entidades se marcan con PDC; al iniciar el servidor se limpian mascotas huérfanas.

### 3.5 Muerte y revivir
- `death.enabled` (toggle): si es `false`, la mascota es inmortal (cancela daño letal).
- `death.pvp` (toggle): si otros jugadores pueden dañarla/matarla.
- Al morir: queda marcada como muerta y su **ítem de alma** va **al inventario del dueño**; si está lleno, cae al suelo junto al dueño.
- `death.keep-skills` (toggle): si al revivir conserva sus nodos desbloqueados y SP asignados, o vuelve a 0 (los SP asignados se devuelven al jugador, el dinero no).
- El ítem de alma es **inmutable**: ID único en PDC vinculado a dueño + instancia; bloqueado en crafteo, yunque, smithing, horno, grindstone, encantamiento, colocación y uso. Si se duplica, solo la primera copia que se use es válida.
- Revivir: LDPotions ejecuta `neopets revive %player%` al beber la poción; el comando verifica que el ítem de alma válido esté **en la mano principal**, lo consume y revive la mascota.

## 4. Entrega de mascotas

- `/neopet give <jugador> <id1>,<id2>,…` → elige al azar (según `weight`) **entre las variantes de la lista que el jugador no posee**. Si las posee todas: mensaje configurable y nada se entrega (o comando de compensación configurable).
- `/neopet give <jugador> random [tipo]` → aleatoria entre todas las variantes (o de un tipo) no poseídas.
- `/neopet give <jugador> <id>` → variante específica.
- Pensado para consola/otros plugins (kit starter, CustomDungeons, LDActivities).
- Compra con dinero (`shop.enabled: false` por defecto).

## 5. Skill tree (árbol de atributos)

### 5.1 Modelo
- Cada **tipo** define su árbol en `skilltrees/<tipo>.yml`. Cada tipo tiene atributos **distintos**; solo un grupo base es común a todos.
- Estructura de cada árbol (22 nodos por defecto, configurable):

| Bloque | Contenido | Nodos |
|---|---|---|
| Común (todos los tipos) | Vida 5 · Regeneración 5 · Resistencia al fuego 1 | 11 |
| Propio del tipo (vanilla) | 2 atributos × 5 niveles | 10 |
| Habilidad única (custom) | 1 habilidad | 1 |
| **Total** | | **22** |

- **Vanilla** = atributos/efectos de Minecraft; implementación genérica reutilizable. **Custom** = clase programada registrada por ID.
- Los nodos de un atributo se desbloquean en orden (nivel 1 → 2 → …).

### 5.2 Árboles por tipo

| Tipo | Atributos propios (5 niveles c/u) | Habilidad única |
|---|---|---|
| Oso polar | Fuerza · Velocidad | **Zarpazo**: daño en área a los mobs hostiles cercanos (cooldown). |
| Perro | Velocidad · Olfato (radio de detección) | **Buscatesoros**: marca con partículas cofres de tesoro, de dungeons y menas cercanas durante unos segundos (cooldown). |
| Gato | Agilidad (velocidad) · Armadura | **Aterrizaje felino**: el dueño no recibe daño por caída mientras el gato está invocado. |
| Loro | Velocidad de vuelo · Alcance de alerta | **Vigía**: sonido de aviso + brillo en los mobs hostiles cercanos. |
| Burro | Armadura · Resistencia al empuje | **Alforjas mensajeras** (ver §5.3). |

El oso polar coincide con la hoja aprobada: Vida 5, Regeneración 5, Fuerza 5, Velocidad 5, Anti-fuego 1, habilidad única 1 = 22.

Catálogo de atributos vanilla disponibles para futuros ajustes: vida, regeneración, armadura, resistencia al daño, fuerza, velocidad, velocidad de vuelo, tamaño (x0.5–x2), resistencia al fuego / caídas / explosiones / empuje, respiración acuática.

### 5.3 Alforjas mensajeras (burro)
- El burro tiene un **cofre sencillo (27 slots)** persistente, accesible por el dueño desde su GUI.
- Desde la GUI, el dueño puede **enviar el burro** a otro jugador conectado. El destinatario recibe una solicitud y debe **aceptarla**.
- Mientras está enviado: el burro aparece junto al destinatario, que puede abrir el cofre, sacar y meter ítems, y pulsar **Devolver**. El burro vuelve al dueño con el contenido.
- Si el destinatario se desconecta, rechaza o pasa el tiempo límite (configurable, p. ej. 5 min), el burro vuelve solo.
- En tránsito es invulnerable y el dueño no puede invocarlo. Solo un jugador puede tener el cofre abierto a la vez (anti-duplicación).
- Al morir el burro, el contenido se conserva con la mascota y se recupera al revivir.
- Cooldown de envío configurable.

### 5.4 Coste: desbloquear y activar
Mejorar un nodo tiene dos pasos:
1. **Desbloquear** el nodo pagando **dinero** (Vault). Coste configurable por atributo y nivel.
2. **Activar** la mejora asignándole **1 SP**.

Reglas:
- Los SP son **por jugador** y se obtienen con `/neopet sp give <jugador> <cantidad>` (CustomDungeons/LDActivities al abrir cofres, etc.).
- `max-skill-points` por rango: tope de SP **asignados por mascota**. Usuario sin rango: **3** (es decir, como máximo 3 mejoras activas en cada mascota). El saldo de SP del jugador no tiene tope.
- `max-nodes-per-pet` por rango: tope de nodos desbloqueados por mascota (usuario 10, borradores VIP 16 / 22).
- Reset de árbol: comando admin; para jugadores, opcional y de pago (toggle). El reset devuelve los SP, no el dinero.

### 5.5 GUI del skill tree
- Menú de cofre: una fila por atributo, un slot por nivel. Gris = bloqueado, amarillo = desbloqueado sin SP, verde = activo, rojo = no disponible (sin dinero/SP/límite).
- Muestra SP disponibles del jugador, SP asignados/tope de la mascota, nodos desbloqueados/máximo y coste del siguiente nodo.

## 6. Rangos y límites

`config.yml` → sección `ranks`, resuelta vía grupo/meta de LuckPerms (fallback: permiso `neopets.rank.<id>`):
```yaml
ranks:
  default: { max-active-pets: 1, max-skill-points: 3, max-nodes-per-pet: 10 }
  # Borradores; el servidor aún no tiene rangos VIP:
  vip:     { max-active-pets: 2, max-skill-points: 6, max-nodes-per-pet: 16 }
  vipplus: { max-active-pets: 4, max-skill-points: 9, max-nodes-per-pet: 22 }
```
Se toma el valor más alto entre los rangos que tenga el jugador.

## 7. Administración

- GUI `/neopet admin` (cofre): activar/desactivar features y editar valores numéricos simples; escribe en `config.yml` y recarga en caliente.
- `/neopet reload`.
- Permisos: `neopets.use`, `neopets.admin`, `neopets.admin.give`, `neopets.admin.sp`, `neopets.admin.revive`, `neopets.admin.reset`, `neopets.admin.gui`, `neopets.admin.reload`.

## 8. PlaceholderAPI
`%neopets_sp%`, `%neopets_sp_max%`, `%neopets_pets_owned%`, `%neopets_pets_active%`, `%neopets_max_active%`, `%neopets_active_pet_name%`.

## 9. Integraciones por comando
| Plugin | Uso |
|---|---|
| Kit starter (externo) | `neopet give <jugador> <ids>` |
| CustomDungeons / LDActivities | `neopet give …`, `neopet sp give …` desde recompensas de cofres |
| LDPotions | `neopets revive %player%` al beber la poción |
