package com.tengames.push;

import com.tengames.common.web.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/push")
public class PushController {

    private final PushService push;

    public PushController(PushService push) {
        this.push = push;
    }

    /** What the browser must pass to pushManager.subscribe. */
    public record SubscriptionRequest(@NotBlank String endpoint, @NotBlank String p256dh, @NotBlank String auth) {
    }

    public record UnsubscribeRequest(@NotBlank String endpoint) {
    }

    /**
     * Public: the client needs the key before it can ask for permission, and
     * an application server's public key is meant to be handed out.
     */
    @GetMapping("/key")
    public Map<String, String> key() {
        return Map.of("publicKey", push.publicKey() == null ? "" : push.publicKey());
    }

    @GetMapping("/subscriptions")
    public Map<String, Boolean> status(Authentication authentication) {
        return Map.of("subscribed", push.isSubscribed(CurrentUser.id(authentication)));
    }

    // 204 rather than an empty 200: the client reads a body when there is one,
    // and "no content" is what this actually is
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/subscriptions")
    public void subscribe(Authentication authentication, @Valid @RequestBody SubscriptionRequest request) {
        push.subscribe(CurrentUser.id(authentication), request.endpoint(), request.p256dh(), request.auth());
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/subscriptions")
    public void unsubscribe(Authentication authentication, @Valid @RequestBody UnsubscribeRequest request) {
        push.unsubscribe(CurrentUser.id(authentication), request.endpoint());
    }
}
