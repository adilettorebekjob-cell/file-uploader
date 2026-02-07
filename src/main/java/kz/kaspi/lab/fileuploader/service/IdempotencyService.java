package kz.kaspi.lab.fileuploader.service;

import kz.kaspi.lab.fileuploader.entity.Upload;
import kz.kaspi.lab.fileuploader.exception.IdempotencyException;
import kz.kaspi.lab.fileuploader.repository.UploadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final UploadRepository uploadRepository;

    /**
     * Проверяет идемпотентность и создаёт запись о начале обработки.
     * Использует optimistic locking через БД (UNIQUE constraint).
     */
    @Transactional
    public Mono<Upload> initiateProcessing(String idempotencyKey, String filename, long size, String contentType) {
        return uploadRepository.existsByIdempotencyKey(idempotencyKey)
                .flatMap(exists -> {
                    if (exists) {
                        log.warn("Duplicate request detected for key: {}", idempotencyKey);
                        return Mono.error(new IdempotencyException(
                                "Request with idempotency key " + idempotencyKey + " already processed"
                        ));
                    }

                    Upload upload = new Upload();
                    upload.setIdempotencyKey(idempotencyKey);
                    upload.setOriginalFilename(filename);
                    upload.setFileSize(size);
                    upload.setContentType(contentType);
                    upload.setStatus(Upload.UploadStatus.PROCESSING);

                    return uploadRepository.save(upload);
                })
                .subscribeOn(Schedulers.boundedElastic()); // Блокирующая операция БД в отдельном пуле
    }

    /**
     * Проверяет статус существующей записи (для повторных запросов).
     */
    public Mono<Upload> checkExistingStatus(String idempotencyKey) {
        return uploadRepository.findByIdempotencyKey(idempotencyKey);
    }
}
