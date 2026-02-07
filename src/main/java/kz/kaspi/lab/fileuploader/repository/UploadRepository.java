package kz.kaspi.lab.fileuploader.repository;

import kz.kaspi.lab.fileuploader.entity.Upload;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface UploadRepository extends ReactiveCrudRepository<Upload, UUID> {

    Mono<Boolean> existsByIdempotencyKey(String idempotencyKey);

    Mono<Upload> findByIdempotencyKey(String idempotencyKey);

    @Query("UPDATE uploads SET status = :status, updated_at = NOW() WHERE idempotency_key = :key")
    Mono<Void> updateStatusByIdempotencyKey(String key, String status);

    @Query("UPDATE uploads SET status = :status, error_message = :errorMessage, updated_at = NOW() WHERE idempotency_key = :key")
    Mono<Void> updateStatusAndErrorByIdempotencyKey(String key, String status, String errorMessage);
}