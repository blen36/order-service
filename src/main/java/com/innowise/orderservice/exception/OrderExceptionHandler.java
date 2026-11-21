package com.innowise.orderservice.exception;

import java.util.List;
import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class OrderExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(OrderExceptionHandler.class);

    @ExceptionHandler(OrderApiException.class)
    ResponseEntity<ProblemDetail> apiError(OrderApiException ex) {
        return problem(ex.status(), ex.getMessage());
    }

    @ExceptionHandler({DataIntegrityViolationException.class, ConcurrencyFailureException.class})
    ResponseEntity<ProblemDetail> conflict(RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, "The resource is in use or was changed concurrently");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var detail = ProblemDetail.forStatusAndDetail(status, "Invalid request fields");
        List<String> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage()).toList();
        detail.setProperty("errors", fields);
        return handleExceptionInternal(ex, detail, headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        LOG.error("Unexpected Order Service failure", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
