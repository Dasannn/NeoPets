# NeoPets — Especificación funcional (MVP)

> Estado: **BORRADOR — pendiente de aprobación**
> Depende de: `docs/constitution.md`

## 1. Alcance del MVP

Incluido:
- Sistema de mascotas: posesión, activar/desactivar, GUI de selección (mascota + skin/variante), muerte y revivir.
- Comandos de entrega (aleatoria y específica), sin repetir variantes ya poseídas.
- Árbol de atributos (skill tree) por tipo de mascota, independiente del sistema de mascotas.
- Límites por rango configurables (LuckPerms).
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
| **Variante** | Combinación concreta de tipo + aspecto vanilla (raza/color/patrón) + flag bebé. Cada variante es una mascota distinta y tiene un ID configurable, p. ej. `parrot_red`, `bird01`. |
| **Mascota (instancia)** | Variante que posee un jugador, con su estado (viva/muerta, nombre, nodos desbloqueados). |
| **Atributo** | Rama del skill tree de un tipo (p. ej. Vida). Tiene N niveles. |
| **Nodo** | Un nivel de un atributo. Se desbloquean en orden dentro del atributo. |
| **SP (skill point)** | Moneda por jugador, se obtiene en dungeons/actividades. |

## 3. Sistema de mascotas

### 3.1 Tipos y variantes
- 5 tipos en el MVP: perro, gato, loro, oso polar, burro.
- Variantes definidas en `pets/<tipo>.yml`: ID, aspecto vanilla (p. ej. variante de lobo, tipo de gato, color de loro, color/marcas de burro), `baby: true|false` (bebé permanente), nombre visible e ítem de icono.
- Cada variante tiene un `weight` (peso) para la entrega aleatoria.

### 3.2 Posesión
- Un jugador puede poseer cualquier número de variantes, pero **nunca dos veces la misma**.
- `max-active-pets` por rango (usuario 2, VIP+ 4, …) limita cuántas mascotas puede tener **invocadas a la vez**. *(Pendiente confirmar — ver §9.)*
- La mascota es permanente salvo que muera (si la muerte está activada).

### 3.3 GUI "Mis mascotas"
- `/neopets` (o `/pets`) abre un menú de cofre paginado con las variantes poseídas.
- Clic: invocar/guardar. Clic derecho: abrir su skill tree. Shift-clic: renombrar (si está activado).
- Mascotas muertas se muestran en gris con indicación de cómo revivirlas.

### 3.4 Comportamiento en el mundo
- Sigue al dueño, se teletransporta si se aleja más de X bloques (configurable), no se puede montar/robar/criar/domesticar por terceros, no despawnea.
- Se guarda (desinvoca) al desconectarse el dueño y se reinvoca al conectarse si estaba activa.
- Las entidades se marcan con PDC; al iniciar el servidor se limpian mascotas huérfanas.

### 3.5 Muerte y revivir
- `death.enabled` (toggle): si es `false`, la mascota es inmortal (cancela daño letal).
- `death.pvp` (toggle): si otros jugadores pueden dañarla/matarla.
- Al morir: queda marcada como muerta y suelta un **ítem de alma** (nombre/material configurable) en el suelo. *(Pendiente confirmar destino — ver §9.)*
- `death.keep-skills` (toggle): si al revivir conserva sus nodos o vuelve a 0.
- El ítem de alma es **inmutable**: ID único en PDC vinculado a dueño + instancia; bloqueado en crafteo, yunque, smithing, horno, grindstone, encantamiento, colocación y uso. Si se duplica, solo la primera copia que se use es válida.
- Revivir: LDPotions ejecuta `neopets revive %player%` al beber la poción; el comando verifica que el ítem de alma válido esté **en la mano principal**, lo consume y revive la mascota.

## 4. Entrega de mascotas

- `/neopet give <jugador> <id1>,<id2>,…` → elige al azar (según `weight`) **entre las variantes de la lista que el jugador no posee**. Si las posee todas: mensaje configurable y nada se entrega (o se ejecuta un comando de compensación configurable).
- `/neopet give <jugador> random [tipo]` → aleatoria entre todas las variantes (o de un tipo) no poseídas.
- `/neopet give <jugador> <id>` → variante específica.
- Pensado para ejecutarse desde consola/otros plugins (kit starter, CustomDungeons, LDActivities).
- Compra con dinero (`shop.enabled: false` por defecto).

## 5. Skill tree (árbol de atributos)

### 5.1 Modelo
- Cada **tipo** define su árbol en `skilltrees/<tipo>.yml`: lista de atributos, cada uno con `max-level`, efecto por nivel y coste por nivel.
- Dos categorías de atributo:
  - **Vanilla**: mapean a atributos/efectos de Minecraft (vida, armadura, fuerza, velocidad, escala, regeneración, resistencia al fuego, …). Implementados genéricamente; se reutilizan en cualquier tipo.
  - **Custom**: habilidades programadas (p. ej. "proteger al dueño"). Cada una es una clase registrada por ID; un tipo la usa referenciándola en su YAML.
- Los nodos de un atributo se desbloquean en orden (nivel 1 → 2 → …).

### 5.2 Ejemplo: Oso polar (22 nodos)

| Atributo | Categoría | Niveles |
|---|---|---|
| Vida | vanilla | 5 |
| Regeneración | vanilla | 5 |
| Fuerza | vanilla | 5 |
| Velocidad | vanilla | 5 |
| Poción anti-fuego | vanilla | 1 |
| Proteger a su dueño | custom | 1 |
| **Total** | | **22** |

### 5.3 Coste y límites
- Desbloquear un nodo cuesta **SP** y/o **dinero (Vault)**, ambos configurables por atributo y nivel (cualquiera puede ser 0).
- Los SP son **por jugador** y se comparten entre todas sus mascotas.
- Los SP se obtienen con `/neopet sp give <jugador> <cantidad>` (ejecutado por CustomDungeons/LDActivities al abrir cofres, etc.).
- Límites por rango (configurables), aplicados por mascota:
  - `max-nodes-per-pet`: usuario 10, VIP 16, VIP++ 22.
  - `max-sp-balance` (opcional): tope de SP acumulados.
- Reset de árbol (comando admin; opcionalmente de pago para jugadores, toggle).

### 5.4 GUI del skill tree
- Menú de cofre: una fila por atributo, un slot por nivel (verde = desbloqueado, gris = disponible, rojo/bloqueado = sin SP/dinero/límite). Igual que la hoja de ejemplo.
- Muestra SP del jugador, nodos usados / máximo por rango y coste del siguiente nodo.

### 5.5 Catálogo inicial de habilidades
Vanilla (compartidas): vida máxima, armadura, resistencia al daño, escala (x0.5–x2), velocidad, regeneración, fuerza de ataque, resistencia al fuego, a caídas, a explosiones, al empuje, respiración acuática.

Custom (MVP, una o dos por tipo; el resto en versiones posteriores):
- Perro: Guardián (ataca a quien golpea al dueño).
- Gato: Ahuyenta creepers.
- Loro: Planeo (caída lenta al dueño).
- Oso polar: Proteger a su dueño (ataca hostiles cercanos).
- Burro: Alforjas (inventario extra).

## 6. Rangos y límites

`config.yml` → sección `ranks`, resuelta vía grupo/meta de LuckPerms (fallback: permiso `neopets.rank.<id>`):
```yaml
ranks:
  default: { max-active-pets: 2, max-nodes-per-pet: 10 }
  vip:     { max-active-pets: 4, max-nodes-per-pet: 16 }
  vipplus: { max-active-pets: 6, max-nodes-per-pet: 22 }
```
Se toma el valor más alto entre los rangos que tenga el jugador.

## 7. Administración

- GUI `/neopet admin` (cofre): activar/desactivar features y editar valores numéricos simples; escribe en `config.yml` y recarga en caliente.
- `/neopet reload`.
- Permisos: `neopets.use`, `neopets.admin`, `neopets.admin.give`, `neopets.admin.sp`, `neopets.admin.revive`, `neopets.admin.reset`, `neopets.admin.gui`, `neopets.admin.reload`.

## 8. PlaceholderAPI
`%neopets_sp%`, `%neopets_pets_owned%`, `%neopets_pets_active%`, `%neopets_max_active%`, `%neopets_active_pet_name%`.

## 9. Preguntas abiertas
1. ¿`máximo 2 pets` = invocadas a la vez (asumido) y la posesión es ilimitada?
2. ¿El tope de "3 SP" del borrador original sigue existiendo como `max-sp-balance`, o el límite real es solo `max-nodes-per-pet`?
3. Ítem de alma: ¿cae al suelo (otro jugador podría recogerlo) o va directo al inventario del dueño?
4. ¿Desbloquear un nodo cuesta SP **y** dinero, o solo uno de los dos? (Diseño: ambos configurables.)
