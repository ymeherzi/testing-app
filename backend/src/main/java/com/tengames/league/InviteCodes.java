package com.tengames.league;

import java.security.SecureRandom;

/** Short human-typable invite codes: unambiguous alphabet, no I/L/O/0/1. */
public final class InviteCodes {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 8;

    private InviteCodes() {
    }

    public static String generate(SecureRandom random) {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    public static String normalize(String input) {
        return input == null ? "" : input.trim().toUpperCase();
    }
}
