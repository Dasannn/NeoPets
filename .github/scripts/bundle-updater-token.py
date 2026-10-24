"""Inject the optional read-only credential into the final release JAR before signing.

Run after Gradle exits: the secret never becomes a Gradle input, processed resource,
source JAR entry, daemon value, or build/configuration cache entry.
"""

import os
import sys
import zipfile


def bundle(jar_path, token):
    if not token:
        print("::notice::NEOPETS_UPDATER_TOKEN is absent; publishing without a bundled updater token")
        return
    # GitHub masks secrets automatically; also mask explicitly before any further work.
    escaped = token.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    print("::add-mask::" + escaped, flush=True)
    if any(ord(char) < 32 or 127 <= ord(char) <= 159 for char in token) or token.isspace():
        raise SystemExit("Invalid updater token [REDACTED]")
    with zipfile.ZipFile(jar_path, "a", compression=zipfile.ZIP_DEFLATED) as jar:
        if "neopets-updater-token.txt" in jar.namelist():
            raise SystemExit("Updater token resource already exists; rebuild the release JAR")
        jar.writestr("neopets-updater-token.txt", token.encode("utf-8"))


if __name__ == "__main__":
    bundle(sys.argv[1], os.environ.get("NEOPETS_UPDATER_TOKEN", ""))
