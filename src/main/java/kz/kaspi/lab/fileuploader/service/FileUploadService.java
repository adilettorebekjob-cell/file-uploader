package kz.kaspi.lab.fileuploader.service;

import kz.kaspi.lab.fileuploader.dto.UploadResponse;
import kz.kaspi.lab.fileuploader.entity.Upload;
import kz.kaspi.lab.fileuploader.exception.StorageException;
import kz.kaspi.lab.fileuploader.repository.UploadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final IdempotencyService idempotencyService;
    private final StorageService storageService;
    private final UploadRepository uploadRepository;
    private final TransactionalOperator transactionalOperator;

    public Mono<UploadResponse> processUpload(
            String idempotencyKey,
            String originalFilename,
            long fileSize,
            String contentType,
            Flux<DataBuffer> fileContent) {

        // Шаг 1 & 2: Проверка идемпотентности и создание записи
        return idempotencyService.initiateProcessing(idempotencyKey, originalFilename, fileSize, contentType)
                .flatMap(upload ->
                        // Шаг 3: Загрузка в хранилище (вне транзакции БД!)
                        storageService.uploadFile(fileContent, originalFilename, fileSize, contentType)
                                .flatMap(storagePath -> {
                                    // Шаг 4: Обновление записи в БД
                                    upload.setStoragePath(storagePath);
                                    upload.setStatus(Upload.UploadStatus.SUCCESS);
                                    return uploadRepository.save(upload)
                                            .onErrorResume(dbError -> {
                                                // Шаг 5: Компенсация — удаляем файл из хранилища
                                                log.error("Database error after successful storage upload. Initiating compensation.", dbError);
                                                return storageService.deleteFile(storagePath)
                                                        .then(updateStatusToFailed(idempotencyKey, dbError.getMessage()))
                                                        .then(Mono.error(dbError));
                                            });
                                })
                                .onErrorResume(StorageException.class, storageError -> {
                                    // Ошибка хранилища — просто обновляем статус
                                    log.error("Storage upload failed", storageError);
                                    return updateStatusToFailed(idempotencyKey, storageError.getMessage())
                                            .then(Mono.error(storageError));
                                })
                )
                .map(this::toResponse);
    }

    private Mono<Void> updateStatusToFailed(String idempotencyKey, String errorMessage) {
        return uploadRepository.updateStatusAndErrorByIdempotencyKey(
                idempotencyKey,
                Upload.UploadStatus.FAILED.name(),
                errorMessage
        );
    }

    private UploadResponse toResponse(Upload upload) {
        return new UploadResponse(
                upload.getId(),
                upload.getStatus(),
                upload.getOriginalFilename(),
                upload.getStoragePath(),
                upload.getCreatedAt()
        );
    }
}
