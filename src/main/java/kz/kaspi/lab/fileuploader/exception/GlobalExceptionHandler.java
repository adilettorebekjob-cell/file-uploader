package kz.kaspi.lab.fileuploader.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IdempotencyException.class)
    public ResponseEntity<?> handleIdempotency(IdempotencyException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "timestamp", Instant.now(),
                        "error", "Conflict",
                        "message", e.getMessage()
                ));
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<?> handleStorage(StorageException e) {
        log.error("Storage error", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "timestamp", Instant.now(),
                        "error", "Storage Error",
                        "message", "Failed to store file"
                ));
    }
}