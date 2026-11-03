package com.cloudforge.common.error;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.cloudforge.common.logging.Redaction;
import com.cloudforge.common.web.CorrelationIdFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every API failure into an RFC 9457 {@code application/problem+json} body with three extra
 * members: {@code code} (stable error code), {@code correlationId} and {@code timestamp}.
 *
 * <p>Spring MVC's own exceptions (malformed JSON, unsupported media type, 404 routes, ...) are
 * handled by the {@link ResponseEntityExceptionHandler} base class. Business exceptions map to
 * status codes here. Anything unexpected becomes a generic 500: the stack trace goes to the log,
 * never to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.code(), ex.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ProblemDetail> handleInvalidRequest(InvalidRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.code(), ex.getMessage());
    }

    @ExceptionHandler(InvalidSpecificationException.class)
    ResponseEntity<ProblemDetail> handleInvalidSpecification(InvalidSpecificationException ex) {
        ResponseEntity<ProblemDetail> response = problem(HttpStatus.UNPROCESSABLE_CONTENT, ex.code(), ex.getMessage());
        response.getBody().setProperty("errors", ex.violations());
        return response;
    }

    @ExceptionHandler(TooManyRequestsException.class)
    ResponseEntity<ProblemDetail> handleTooManyRequests(TooManyRequestsException ex) {
        ProblemDetail problem = problem(HttpStatus.TOO_MANY_REQUESTS, ex.code(), ex.getMessage()).getBody();
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, ex.retryAfter().toSeconds())))
                .body(problem);
    }

    @ExceptionHandler(PreconditionFailedException.class)
    ResponseEntity<ProblemDetail> handlePreconditionFailed(PreconditionFailedException ex) {
        return problem(HttpStatus.PRECONDITION_FAILED, ex.code(), ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ProblemDetail> handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, ex.code(), ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    ResponseEntity<ProblemDetail> handleBusinessRule(BusinessRuleViolationException ex) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, ex.code(), ex.getMessage());
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    ResponseEntity<ProblemDetail> handleConcurrentModification(RuntimeException ex) {
        log.info("Concurrent modification rejected: {}", ex.getClass().getSimpleName());
        return problem(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "The resource was modified concurrently. Reload it and try again.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        // The constraint message can contain table names and row values, so keep it in the log only.
        log.warn("Data integrity violation", Redaction.redact(ex));
        return problem(HttpStatus.CONFLICT, "DATA_CONFLICT",
                "The request conflicts with existing data.");
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    ResponseEntity<ProblemDetail> handleAuthenticationFailed(AuthenticationFailedException ex) {
        return problem(HttpStatus.UNAUTHORIZED, ex.code(), ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                "Authentication is required to access this resource.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "You do not have permission to perform this operation.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", Redaction.redact(ex));
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Quote the correlation ID when reporting it.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ex.getBody();
        body.setDetail("The request contains invalid fields.");
        body.setProperty("code", "VALIDATION_FAILED");
        body.setProperty("errors", fieldErrors(ex));
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /** Hook used by the base class for every Spring MVC exception: add the common members. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            enrich(problem, defaultCode(statusCode));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        enrich(problem, code);
        return ResponseEntity.status(status).body(problem);
    }

    private void enrich(ProblemDetail problem, String fallbackCode) {
        if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
            problem.setProperty("code", fallbackCode);
        }
        problem.setProperty("correlationId", CorrelationIdFilter.current());
        problem.setProperty("timestamp", Instant.now(clock));
    }

    private static String defaultCode(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status != null ? status.name() : "HTTP_" + statusCode.value();
    }

    private static List<FieldViolation> fieldErrors(MethodArgumentNotValidException ex) {
        return ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toViolation)
                .toList();
    }

    private static FieldViolation toViolation(FieldError error) {
        // Rejected values are deliberately omitted: they may contain passwords or other secrets.
        return new FieldViolation(error.getField(), error.getDefaultMessage());
    }
}
