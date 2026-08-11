package com.tengames.auth;

import com.tengames.common.web.RejectedException;

/** A password the policy refuses, with the rule it broke. */
public class WeakPasswordException extends RejectedException {

    public WeakPasswordException(String code, String message) {
        super(code, "password", message);
    }
}
