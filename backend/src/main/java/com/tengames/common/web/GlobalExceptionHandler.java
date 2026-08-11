package com.tengames.common.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        problem.setProperty("errors", errors);
        // Clients show `detail`; without it the caller gets a bare "Validation
        // failed" and no idea which field to fix.
        problem.setDetail(errors.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(java.util.stream.Collectors.joining("; ")));
        return problem;
    }

    /**
     * Carries the reason we threw with ("That code is not right") through to
     * the client; without this the framework answers with a bare status and
     * the UI can only show a generic failure.
     */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<ProblemDetail> handleStatus(
            org.springframework.web.server.ResponseStatusException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(ex.getStatusCode());
        if (ex.getReason() != null) {
            problem.setDetail(ex.getReason());
        }
        return org.springframework.http.ResponseEntity.status(ex.getStatusCode()).body(problem);
    }

    /**
     * Refused input — a weak password, an address that cannot receive mail.
     * The code travels beside the sentence so the client can say it in the
     * player's own language, and the errors map puts the message under the
     * field the form already displays.
     */
    @ExceptionHandler(RejectedException.class)
    public ProblemDetail handleRejected(RejectedException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Rejected");
        problem.setDetail(ex.getMessage());
        problem.setProperty("code", ex.getCode());
        problem.setProperty("errors", Map.of(ex.getField(), ex.getMessage()));
        return problem;
    }

    /**
     * The account is unusable until its code arrives, so a failed send is
     * reported rather than hidden behind a cheerful "code sent".
     */
    @ExceptionHandler(com.tengames.auth.email.MailDeliveryException.class)
    public ProblemDetail handleMailFailure(com.tengames.auth.email.MailDeliveryException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_GATEWAY);
        problem.setDetail("We couldn't send your code — try again in a moment");
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Request conflicts with existing data");
        return problem;
    }
}
