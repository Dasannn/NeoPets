# Guía de diseño de GUIs

Todas las GUIs de NeoPets siguen estas reglas para verse ordenadas, limpias y coherentes.

## Estructura

- Menús de cofre de 6 filas (54 slots) salvo que el contenido justifique menos.
- **Marco**: fila superior e inferior con `BLACK_STAINED_GLASS_PANE` en las esquinas y
  `GRAY_STAINED_GLASS_PANE` en el resto; columnas laterales con `GRAY_STAINED_GLASS_PANE`.
  Todos los paneles sin nombre visible (" ") y sin lore.
- **Cabecera**: slot 4, un ítem informativo del contexto (p. ej. `NETHER_STAR` en "Mis mascotas").
- **Contenido**: el área interior de 7×4 (slots 10–16, 19–25, 28–34, 37–43), llenada de izquierda
  a derecha y de arriba abajo, sin huecos.
- **Barra de control** (fila inferior), siempre en las mismas posiciones:
  - 47 filtro/orden (`HOPPER`) o acción secundaria del menú, 48 página anterior (`ARROW`), 49 cerrar
    (`BARRIER`) o volver al menú anterior (`OAK_DOOR`, nunca una flecha), 50 página siguiente (`ARROW`),
    51 ayuda (`BOOK`).
  - Un botón sin acción posible (p. ej. no hay página anterior) se muestra en gris
    (`<gray>`) o se sustituye por panel de marco.

## Ítems

- Cada tipo de elemento usa un material distinto y reconocible; nunca el mismo ícono para
  acciones diferentes.
- Mascotas: huevo de spawn de su tipo (`WOLF_SPAWN_EGG`, `CAT_SPAWN_EGG`, `PARROT_SPAWN_EGG`,
  `POLAR_BEAR_SPAWN_EGG`, `DONKEY_SPAWN_EGG`) o el `icon` configurado en la variante.
  - Invocada: brillo de encantamiento.
  - Muerta: `GRAY_DYE` con nombre en rojo.
- Elementos de estado (p. ej. nodos de un árbol) usan ítems distintos de los paneles del marco
  (tintes por estado), para no confundirse con el marco ni el relleno. Los slots sin contenido
  dentro de una fila de datos se dejan vacíos.
- Valores de mejoras siempre relativos: `+8 de vida`, `+10% de velocidad`.
- Nada de texto en cursiva por defecto (desactivar la cursiva de lore/nombres).

## Texto (MiniMessage)

- Nombre: color de estado — `<green>` activo/disponible, `<white>` normal, `<red>` muerto o
  bloqueado, `<gold><bold>` cabeceras.
- Primera línea de lore: subtítulo en `<dark_gray>` (tipo · edad).
- Línea vacía separando bloques: información → acciones.
- Datos: etiqueta en `<gray>`, valor en `<white>` o color semántico (`<aqua>` para SP).
- Acciones al final: `<yellow>Clic izq. <dark_gray>» <gray>Acción`.
- Estados con símbolo: `● Invocada` (verde), `○ Guardada` (amarillo), `✖ Muerta` (rojo).

## Revisión visual

Cada GUI define su contenido como datos (`MenuLayout`) y los tests exportan cada estado a JSON.
`tooling/gui-preview/render.py` los convierte en PNG con las texturas del cliente de Minecraft
(extraídas localmente, nunca versionadas). Ninguna GUI se integra sin que sus capturas hayan sido
revisadas y aprobadas. Ejemplo de referencia: `tooling/gui-preview/examples/pets-menu.json`.
