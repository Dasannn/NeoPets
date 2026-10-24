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

## Token de lectura para actualizaciones

Las releases oficiales pueden incluir un token de lectura: los administradores
ya no necesitan editar `config.yml` para autenticarse. Instalar manualmente una
primera release oficial con la clave pública y el token incluidos; los JAR de
desarrollo y CI no llevan token. Si el propietario publica sin el secret opcional,
el administrador tendrá que proporcionar sus propias credenciales.

El propietario genera en GitHub → Settings de la cuenta → Developer settings →
Personal access tokens → Fine-grained tokens un token con propietario de recurso
correcto, acceso solo a `NeoPets` y permiso de repositorio **Contents: Read-only**
(Metadata se incluye automáticamente). Autorizarlo en la organización si se exige
y elegir caducidad. Guardarlo como secret de Actions **`NEOPETS_UPDATER_TOKEN`**.
No confundirlo con `NEOPETS_UPDATE_SIGNING_KEY`, la clave privada de firma.

**El token incluido no es un secreto frente a quien recibe el JAR:** cualquiera
con una copia puede extraer `neopets-updater-token.txt` y usar sus permisos de
lectura. No concede escritura; no añadir permisos ni acceso a otros repositorios.
No se ofrece ofuscación como protección. Para revocarlo o rotarlo: revocar el token
en GitHub, generar otro de solo lectura, actualizar `NEOPETS_UPDATER_TOKEN` y
publicar una nueva release. Los JAR antiguos conservan el token revocado; instalar
manualmente la nueva release o usar temporalmente credenciales propias para actualizar.

La precedencia es `NEOPETS_GITHUB_TOKEN` del proceso Java →
`updater.github-token` no vacío → token incluido → sin token (`NO_TOKEN`). Un valor
de entorno explícitamente vacío conserva su comportamiento: impide autenticarse,
incluso con token incluido. Un valor de config solo con espacios permite el fallback.
Las credenciales propias siguen siendo opcionales:

```yaml
updater:
  enabled: true
  repository: "Dasannn/NeoPets"
  github-token: "" # las releases oficiales usan el token incluido como fallback
  include-prereleases: false
```

Proteger el archivo y el entorno contra lectura de terceros. La configuración se
aplica tras `/neopet reload`; cambios en el entorno requieren reiniciar el proceso.
Nunca publicar tokens en logs, incidencias ni repositorios.

## Publicar e instalar

Con la clave pública ya incluida y el secret de firma configurado:

```bash
git tag v0.2.0
git push --tags
```

El workflow usa Java 25, ejecuta `./gradlew build -Pversion=0.2.0`, produce
`NeoPets-0.2.0.jar`, `.jar.sha256` y `.jar.sig` (firma binaria de 64 bytes),
comprueba que la clave de firma coincide con la pública y publica los tres assets.
Falla si falta `NEOPETS_UPDATE_SIGNING_KEY`. Si falta `NEOPETS_UPDATER_TOKEN`,
publica igualmente y muestra un aviso sin su valor. El token opcional se añade al
JAR **después de Gradle y antes del checksum y la firma**, por lo que queda cubierto
por ambos. Gradle nunca recibe ese secret: no entra en recursos procesados,
fuentes, daemon ni cachés de compilación/configuración. Solo el JAR final lo lleva;
no ejecutar Gradle con el token como propiedad ni variable de entorno. Los builds
locales (`./gradlew build`) y `build.yml` no lo incluyen. Etiquetas con sufijo de
prerelease se publican como tales.

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
