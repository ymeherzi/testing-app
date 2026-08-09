package com.tengames.auth.email;

/**
 * The provider refused or could not take the message.
 *
 * <p>Unchecked and allowed to escape the auth flow on purpose: an account
 * whose code was never delivered cannot be verified, so reporting failure
 * beats a signup that looks successful and leaves the user waiting on an
 * email that will never arrive.
 */
public class MailDeliveryException extends RuntimeException {

    public MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
