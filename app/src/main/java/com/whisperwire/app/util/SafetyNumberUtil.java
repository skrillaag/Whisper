package com.whisperwire.app.util;

import java.security.MessageDigest;

/**
 * SafetyNumberUtil
 *
 * Computes a human-readable “safety number” used in ChatSecurityActivity.
 * This verifies that both participants are using the correct long-term
 * identity keys (prevents MITM attacks).
 *
 * How it works:
 *   1. Order the two users canonically by UID so both sides compute
 *      the same input string.
 *   2. Concatenate UIDs + identity public keys in that fixed order.
 *   3. Hash using SHA-256.
 *   4. Convert hash → hex fingerprint grouped for readability,
 *      e.g., “AB12 F3CC 9910 …”
 *
 * This mirrors the concept used in apps like Signal (safety numbers),
 * but simplified for this academic project.
 */
public class SafetyNumberUtil {

    /**
     * Computes a deterministic safety number for a pair of users.
     *
     * @param uidA  current user's UID
     * @param uidB  contact's UID
     * @param pubA  Base64-encoded public key of user A
     * @param pubB  Base64-encoded public key of user B
     * @return fingerprint string or "Error" if hashing fails
     */
    public static String computeSafetyNumber(String uidA,
                                             String uidB,
                                             String pubA,
                                             String pubB) {
        try {
            // Determine canonical UID order → ensures both sides produce identical result
            String minUid = uidA.compareTo(uidB) < 0 ? uidA : uidB;
            String maxUid = uidA.compareTo(uidB) < 0 ? uidB : uidA;

            // Assign keys according to ordering above
            String minKey = uidA.equals(minUid) ? pubA : pubB;
            String maxKey = uidA.equals(minUid) ? pubB : pubA;

            // Input string for hashing
            String data = minUid + minKey + maxUid + maxKey;

            // SHA-256 hash of the concatenated identity material
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha.digest(data.getBytes());

            // Convert binary hash → human-readable fingerprint
            return formatFingerprint(hash);

        } catch (Exception e) {
            // Bad state (should not happen in normal use)
            return "Error";
        }
    }

    /**
     * Converts a byte[] hash into a spaced hex fingerprint:
     *   “AB12 45CD 9981 …”
     */
    private static String formatFingerprint(byte[] bytes) {
        // Build continuous uppercase hex string
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02X", b));
        }

        // Insert spaces every 4 characters for readability
        StringBuilder chunked = new StringBuilder();
        for (int i = 0; i < hex.length(); i += 4) {
            if (i > 0) chunked.append(" ");
            int end = Math.min(i + 4, hex.length());
            chunked.append(hex.substring(i, end));
        }

        return chunked.toString();
    }
}
