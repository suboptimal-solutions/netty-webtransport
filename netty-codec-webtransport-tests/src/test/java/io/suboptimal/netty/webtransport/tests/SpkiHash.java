package io.suboptimal.netty.webtransport.tests;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * Computes the value Chrome's {@code --ignore-certificate-errors-spki-list} flag expects:
 * base64(sha256(DER-encoded SubjectPublicKeyInfo)).
 *
 * <p>{@link java.security.PublicKey#getEncoded()} returns the SubjectPublicKeyInfo DER for
 * X.509-format keys, which is what we need.
 */
final class SpkiHash {

    private SpkiHash() {}

    static String of(X509Certificate cert) {
        byte[] spki = cert.getPublicKey().getEncoded();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(spki);
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
