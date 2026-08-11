package com.tengames.common.web;

/**
 * Input refused for a reason the player can act on.
 *
 * <p>Carries a code as well as a sentence, because the app is bilingual and a
 * server string reaching the screen untranslated is exactly what this avoids:
 * the client looks the code up and falls back to the sentence. The field name
 * puts the message where the form already shows one.
 */
public class RejectedException extends RuntimeException {

    private final String code;
    private final String field;

    public RejectedException(String code, String field, String message) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public String getCode() {
        return code;
    }

    public String getField() {
        return field;
    }
}
