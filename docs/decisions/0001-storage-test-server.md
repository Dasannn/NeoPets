# ADR 0001 — Almacenamiento en el servidor de pruebas

- Fecha: 2026-10-23
- Estado: Aceptada

## Contexto
El plugin soporta MySQL/MariaDB y SQLite (ARCHITECTURE §6). El servidor de
pruebas solo dispone del MySQL del proveedor (`127.0.0.1:3307`), compartido con
otros plugins y con credenciales ajenas a NeoPets.

## Decisión
- `storage.type` por defecto: `sqlite` (archivo en `plugins/NeoPets/`).
- El servidor de pruebas usa SQLite: cero configuración, datos aislados y
  reseteables sin tocar bases de otros plugins.
- MySQL sigue siendo el objetivo de producción; su ruta se cubre con tests de
  integración y se validará en el servidor cuando exista una base dedicada.
- Todas las tablas usan el prefijo `np_` para poder compartir una base MySQL
  sin colisiones.

## Consecuencias
- Las migraciones deben ser compatibles con ambos dialectos (o tener variante
  por dialecto).
- Para pasar a MySQL basta cambiar `storage.type` y credenciales en
  `config.yml`; no hay migración automática de datos SQLite → MySQL en el MVP.
