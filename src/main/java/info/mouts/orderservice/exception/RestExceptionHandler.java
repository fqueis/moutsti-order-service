package info.mouts.orderservice.exception;

import java.net.URI;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class RestExceptionHandler {
    /**
     * Capture {@link OrderNotFoundException} and returns HTTP 404 Not Found.
     * Uses the ProblemDetail (RFC 7807) format for the response.
     */
    @ExceptionHandler(OrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail handleOrderNotFoundException(OrderNotFoundException ex, WebRequest request) {
        log.warn("Handling OrderNotFoundException: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Order Not Found");
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setInstance(URI.create(request.getDescription(false)));

        return problemDetail;
    }

    /**
     * Capture {@link OrderItemNotFoundException} and returns HTTP 404 Not Found.
     * Uses the ProblemDetail (RFC 7807) format for the response.
     */
    @ExceptionHandler(OrderItemNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail handleOrderItemNotFoundException(OrderItemNotFoundException ex, WebRequest request) {
        log.warn("Handling OrderItemNotFoundException: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Order Item Not Found");
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setInstance(URI.create(request.getDescription(false)));

        return problemDetail;
    }

    /**
     * Capture {@link MethodArgumentTypeMismatchException} and returns HTTP 400 Bad
     * Request.
     * Uses the ProblemDetail (RFC 7807) format for the response.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex,
            WebRequest request) {
        log.warn("Handling MethodArgumentTypeMismatchException: {}", ex.getMessage());

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Invalid UUID");
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setInstance(URI.create(request.getDescription(false)));

        return problemDetail;
    }

    /**
     * Capture any {@link Exception} and returns HTTP 500 Internal Server Error.
     * Uses the ProblemDetail (RFC 7807) format for the response.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ProblemDetail handleGenericException(Exception ex, WebRequest request) {
        log.error("Handling unexpected exception: {}", ex.getMessage(), ex);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal error occurred.");
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setInstance(URI.create(request.getDescription(false)));

        return problemDetail;
    }
}
