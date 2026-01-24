package com.whisperwire.app.crypto;

import android.util.Log;

import java.security.GeneralSecurityException;
import javax.crypto.KeyAgreement;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.nio.charset.StandardCharsets;

/**
 * Runs a local performance self-test of the WhisperWire crypto pipeline:
 * ECDH (secp256r1) -> HKDF-SHA256 -> AES-GCM-128.
 *
 * This is only for benchmarks and demonstration, never used for real messages.
 */
public class CryptoBenchmark {

    private static final String TAG = "CryptoBenchmark";

    // I reuse the same HKDF + AES-GCM components as the main message pipeline.
    private final HkdfSha256 hkdfSha256;
    private final AesGcmCipher aesGcmCipher;
    // Local PRNG just for benchmark salts and nonces.
    private final SecureRandom secureRandom = new SecureRandom();

    public CryptoBenchmark() {
        this.hkdfSha256 = new HkdfSha256();
        this.aesGcmCipher = new AesGcmCipher();
    }

    // Runs the full ECDH -> HKDF -> AES-GCM roundtrip and logs timing information.
    public String runSelfTest() throws GeneralSecurityException {
        StringBuilder sb = new StringBuilder();
        sb.append("WhisperWire Crypto Self-Test\n");
        sb.append("----------------------------\n");

        // 1) ECDH benchmark (two ephemeral P-256 keypairs)
        long t1 = System.nanoTime();

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1")); // Same curve as identity keys.
        KeyPair a = kpg.generateKeyPair();
        KeyPair b = kpg.generateKeyPair();

        // Each side performs ECDH using its private key and the other's public key.
        KeyAgreement kaA = KeyAgreement.getInstance("ECDH");
        kaA.init(a.getPrivate());
        kaA.doPhase(b.getPublic(), true);
        byte[] sharedA = kaA.generateSecret();

        KeyAgreement kaB = KeyAgreement.getInstance("ECDH");
        kaB.init(b.getPrivate());
        kaB.doPhase(a.getPublic(), true);
        byte[] sharedB = kaB.generateSecret();

        long t2 = System.nanoTime();

        // I verify both parties derived the same shared secret.
        if (!java.util.Arrays.equals(sharedA, sharedB)) {
            throw new GeneralSecurityException("ECDH shared secrets do not match");
        }

        double ecdhMs = (t2 - t1) / 1_000_000.0;
        sb.append("ECDH (P-256) time: ").append(ecdhMs).append(" ms\n");
        sb.append("ECDH shared secret length: ").append(sharedA.length).append(" bytes\n");

        Log.i(TAG, "ECDH (P-256) time: " + ecdhMs + " ms");

        // 2) HKDF-SHA256 benchmark
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt); // Random salt to simulate per-session or per-message salt.
        byte[] info = "WhisperWire:benchmark".getBytes(StandardCharsets.UTF_8); // Context label so keys are not reused with real traffic.

        long t3 = System.nanoTime();
        // I derive a single AES-128 key from the shared secret.
        byte[] aesKey = hkdfSha256.deriveAes128Key(sharedA, salt, info);
        long t4 = System.nanoTime();

        double hkdfMs = (t4 - t3) / 1_000_000.0;
        sb.append("HKDF-SHA256 time: ").append(hkdfMs).append(" ms\n");
        sb.append("HKDF output length (AES-128 key): ").append(aesKey.length).append(" bytes\n");

        Log.i(TAG, "HKDF-SHA256 time: " + hkdfMs + " ms");

        // 3) AES-GCM encrypt/decrypt benchmark
        String testPlaintext = "This is a benchmark test message.";
        byte[] plaintextBytes = testPlaintext.getBytes(StandardCharsets.UTF_8);

        long t5 = System.nanoTime();
        // For the benchmark I skip AAD to focus on raw AEAD performance.
        AesGcmCipher.Ciphertext cipher = aesGcmCipher.encrypt(aesKey, plaintextBytes, null);
        long t6 = System.nanoTime();

        double encMs = (t6 - t5) / 1_000_000.0;
        sb.append("AES-GCM encrypt time: ").append(encMs).append(" ms\n");
        sb.append("Ciphertext length (incl tag): ").append(cipher.getCiphertext().length).append(" bytes\n");

        Log.i(TAG, "AES-GCM encrypt time: " + encMs + " ms");

        long t7 = System.nanoTime();
        byte[] decrypted = aesGcmCipher.decrypt(aesKey, cipher, null);
        long t8 = System.nanoTime();

        double decMs = (t8 - t7) / 1_000_000.0;
        sb.append("AES-GCM decrypt time: ").append(decMs).append(" ms\n");

        Log.i(TAG, "AES-GCM decrypt time: " + decMs + " ms");

        String decryptedText = new String(decrypted, StandardCharsets.UTF_8);
        // I assert the AES-GCM roundtrip to make sure the pipeline is correct.
        if (!testPlaintext.equals(decryptedText)) {
            throw new GeneralSecurityException("AES-GCM decrypt mismatch");
        }

        sb.append("\nResult: OK (shared secret match, AES-GCM roundtrip verified)\n");

        Log.i(TAG, "Crypto self-test completed successfully");
        return sb.toString();
    }
}
