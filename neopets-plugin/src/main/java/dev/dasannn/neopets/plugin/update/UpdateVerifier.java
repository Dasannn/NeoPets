package dev.dasannn.neopets.plugin.update;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.jar.JarInputStream;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** SHA-256 and Ed25519 verification of exactly the downloaded JAR bytes. */
public final class UpdateVerifier {
    private UpdateVerifier() { }

    /** Reads the embedded X.509 public key; missing/placeholder keys fail closed. */
    public static PublicKey publicKey(byte[] pem) throws Exception {
        if (pem == null) return null;
        String text = new String(pem, StandardCharsets.US_ASCII);
        if (text.contains("PLACEHOLDER")) return null;
        String body = text.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(body)));
    }

    /** Checks the authenticated JAR's own version before staging; call only after signature verification. */
    public static UpdateResult.Failure verifyVersion(byte[] jar, String release, SemanticVersion installed) {
        try (var archive = new JarInputStream(new ByteArrayInputStream(jar))) {
            for (var entry = archive.getNextJarEntry(); entry != null; entry = archive.getNextJarEntry()) {
                if (!entry.getName().equals("paper-plugin.yml")) continue;
                var descriptor = new YamlConfiguration();
                descriptor.loadFromString(new String(archive.readAllBytes(), StandardCharsets.UTF_8));
                if (!(descriptor.get("version") instanceof String version)) return UpdateResult.Failure.VERSION_MISMATCH;
                String expected = release.startsWith("v") ? release.substring(1) : release;
                if (!version.equals(expected)) return UpdateResult.Failure.VERSION_MISMATCH;
                return SemanticVersion.parse(version).compareTo(installed) > 0
                        ? null : UpdateResult.Failure.NO_NEWER_VERSION;
            }
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException invalid) {
            return UpdateResult.Failure.VERSION_MISMATCH;
        }
        return UpdateResult.Failure.VERSION_MISMATCH;
    }

    /** Accepts sha256sum format, followed by a raw 64-byte Ed25519 signature. */
    public static UpdateResult.Failure verify(byte[] jar, byte[] checksum, byte[] signature, PublicKey key)
            throws Exception {
        if (key == null) return UpdateResult.Failure.PLACEHOLDER_KEY;
        String expected = new String(checksum, StandardCharsets.US_ASCII).trim().split("\\s+", 2)[0];
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jar));
        if (!expected.matches("[a-fA-F0-9]{64}") || !actual.equalsIgnoreCase(expected)) {
            return UpdateResult.Failure.CHECKSUM_MISMATCH;
        }
        if (signature.length != 64) return UpdateResult.Failure.BAD_SIGNATURE;
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(key);
        verifier.update(jar);
        try {
            return verifier.verify(signature) ? null : UpdateResult.Failure.BAD_SIGNATURE;
        } catch (java.security.SignatureException invalid) {
            return UpdateResult.Failure.BAD_SIGNATURE;
        }
    }
}
