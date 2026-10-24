# Actualizaciones firmadas

NeoPets consulta las releases del repositorio privado `Dasannn/NeoPets`. La descarga
solo se instala tras comprobar SHA-256 y una firma Ed25519 contra la clave pública
incluida en el JAR. No se aceptan versiones iguales ni anteriores. No hay recarga
en caliente del plugin: Paper sustituye el JAR al reiniciar.

## Preparación del propietario

Generar las claves **localmente**, fuera del repositorio:

```bash
umask 077
openssl genpkey -algorithm ED25519 -out neopets-update-private-key.pem
openssl pkey -in neopets-update-private-key.pem -pubout -out neopets-update-public-key.pem
```

Guardar una copia segura de la clave privada. En GitHub → repositorio → Settings →
Secrets and variables → Actions, crear `NEOPETS_UPDATE_SIGNING_KEY` con **todo el PEM
privado**, incluidas las líneas BEGIN/END. Nunca subir esa clave al repositorio ni
mostrarla en logs. Copiar únicamente el PEM **público** X.509 SubjectPublicKeyInfo a
`neopets-plugin/src/main/resources/neopets-update-public-key.pem`, sustituyendo
PLACEHOLDER, y versionar ese archivo. Instalar manualmente un JAR con esa clave
antes de usar el actualizador. Mientras exista PLACEHOLDER, se rechazan instalaciones.
Para rotar la clave, distribuir manualmente un JAR con la nueva clave pública.

## Token del servidor

En GitHub → Settings de la cuenta → Developer settings → Personal access tokens →
Fine-grained tokens, generar un token con propietario de recurso correcto, acceso
solo a `NeoPets` y permiso de repositorio **Contents: Read-only** (Metadata se incluye
automáticamente). Autorizarlo en la organización si se exige y elegir caducidad.

Pegar el token en el `config.yml` del servidor:

```yaml
updater:
  enabled: true
  repository: "Dasannn/NeoPets"
  github-token: "TOKEN_DE_SOLO_LECTURA"
  include-prereleases: false
```

También se acepta `NEOPETS_GITHUB_TOKEN` en el entorno del proceso Java; tiene
prioridad sobre el archivo, incluso si está vacío. Proteger el archivo y el entorno
contra lectura de terceros. No confundir este token de lectura con la clave privada
de firma. La configuración se aplica tras `/neopet reload`; cambios en el entorno
requieren reiniciar el proceso.

## Publicar e instalar

Con la clave pública ya incluida y el secret configurado:

```bash
git tag v0.2.0
git push --tags
```

El workflow usa Java 25, ejecuta `./gradlew build -Pversion=0.2.0`, produce
`NeoPets-0.2.0.jar`, `.jar.sha256` y `.jar.sig` (firma binaria de 64 bytes),
comprueba que la clave de firma coincide con la pública y publica los tres assets.
Falla si falta el secret. Etiquetas con sufijo de prerelease se publican como tales.

Desde consola o con `neopets.admin.update`:

- `/neopets update check`: muestra versión instalada, última release, título y
  primera línea de notas; no descarga ni instala.
- `/neopets update`: descarga, verifica y prepara el JAR con el nombre del JAR
  actualmente cargado en la carpeta de actualización del servidor. Reiniciar
  normalmente cuando indique que está preparado.

Solo se permite una operación simultánea. `include-prereleases: true` compara las
releases publicadas por versión semántica, incluidas versiones previas; SNAPSHOT
es una versión previa de su misma versión base. Los errores de acceso, límite de
API, assets ausentes, checksum o firma inválidos nunca instalan el archivo.
La autorización se retira al seguir redirecciones hacia hosts externos, siguiendo
el [contrato de descarga de assets de GitHub](https://docs.github.com/en/rest/releases/assets).
