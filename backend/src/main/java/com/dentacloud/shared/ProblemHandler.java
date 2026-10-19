package com.dentacloud.shared;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
class ProblemHandler {
    @ExceptionHandler(ApiException.class)
    ProblemDetail api(ApiException e) {
        return problem(e.status(), e.code(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage()).collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "validation_failed", detail);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail stale(ObjectOptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "stale_version", "Someone else changed this record; reload and retry");
    }

    /** Backstop for constraint violations that no service translated (e.g. an id already used elsewhere). */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ProblemDetail integrity(org.springframework.dao.DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "conflict", "This change conflicts with existing data");
    }

    static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setProperty("code", code);
        return p;
    }
}
