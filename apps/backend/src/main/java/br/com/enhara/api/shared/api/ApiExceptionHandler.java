package br.com.enhara.api.shared.api;

import br.com.enhara.api.shared.error.ConflictException;
import br.com.enhara.api.shared.error.ResourceNotFoundException;
import br.com.enhara.api.shared.api.ApiModels.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> notFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> conflict(ConflictException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(field -> fields.putIfAbsent(field.getField(), field.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "Dados inválidos", request, fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiError> parameterValidation(HandlerMethodValidationException exception,
                                                  HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Parâmetros inválidos", request, Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException exception,
                                           HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "Parâmetro inválido: " + exception.getName(), request, Map.of());
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message, HttpServletRequest request,
                                           Map<String, String> fieldErrors) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(),
                status.getReasonPhrase(), message, request.getRequestURI(), fieldErrors));
    }
}
