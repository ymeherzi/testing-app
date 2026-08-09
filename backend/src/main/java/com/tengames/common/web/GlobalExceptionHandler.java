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
