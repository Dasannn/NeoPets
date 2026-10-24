package dev.dasannn.neopets.plugin.update;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

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
