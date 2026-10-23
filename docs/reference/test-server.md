# Servidor de pruebas

Datos verificados el 2026-10-23 vía API de Crafty. Sin credenciales: el token
de Crafty vive fuera del repositorio.

| Dato | Valor |
|---|---|
| Panel | Crafty Controller — `https://8080-agqvsroda4lyjadje5zkb7qhie.instabox.tech/` |
| Servidor principal | "Servidor Agentes" (`8f652573-fdd9-4fd0-93e9-994297a9be87`) |
| Copia | "Servidor Agentes (copia)" (`5e22d5d7-7182-4217-a691-575c51e92009`) |
| Paper | `26.3-211` — API `26.3.build.211-stable` |
| Memoria | `-Xms1024M -Xmx3072M` |
| Puerto | 25565 |

## Plugins relevantes instalados
- Vault 2.1.1, EssentialsX 2.22.1 (economía vía Vault), LuckPerms 5.5.81, PlaceholderAPI 2.12.3.
- CustomDungeons 1.3.0, LDActivities 1.0.0-SNAPSHOT, LastDarePotions (LDPotions), LastDareCore.
- Otros: Towny, WorldGuard, FAWE, Multiverse, CoreProtect, NBTAPI, ViaVersion/ViaBackwards.

## Base de datos
- Existe un MySQL del proveedor en `127.0.0.1:3307` (lo usa DamageControl). Credenciales no documentadas aquí.

## Despliegue
- La API de Crafty permite subir archivos a `plugins/`, borrar y reiniciar (`/api/v2/servers/{id}/action/restart_server`).
