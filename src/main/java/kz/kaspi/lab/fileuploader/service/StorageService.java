package kz.kaspi.lab.fileuploader.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.*;
import kz.kaspi.lab.fileuploader.exception.StorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

    private final MinioClient minioClient;

    @Value("${storage.minio.bucket-name}")
    private String bucketName;

    /**
     * Загружает файл во временный файл, затем в MinIO.
     */
    public Mono<String> uploadFile(Flux<DataBuffer> content, String originalFilename, long fileSize, String contentType) {
        return Mono.fromCallable(() -> {
                    // Создаём временный файл
                    Path tempFile = Files.createTempFile("upload-", "-" + originalFilename);

                    try {
                        // Сохраняем реактивный поток во временный файл
                        DataBufferUtils.write(content, tempFile, StandardOpenOption.WRITE)
                                .share()
                                .block();

                        // Загружаем в MinIO
                        String objectName = generateObjectName(originalFilename);

                        try (InputStream is = Files.newInputStream(tempFile)) {
                            minioClient.putObject(
                                    PutObjectArgs.builder()
                                            .bucket(bucketName)
                                            .object(objectName)
                                            .stream(is, Files.size(tempFile), -1)
                                            .contentType(contentType)
                                            .build()
                            );
                        }

                        log.info("File uploaded to MinIO: {}", objectName);
                        return objectName;

                    } finally {
                        // Удаляем временный файл в любом случае
                        Files.deleteIfExists(tempFile);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(e -> {
                    log.error("Failed to upload file to storage", e);
                    return new StorageException("Failed to upload file: " + e.getMessage(), e);
                });
    }

    /**
     * Удаляет файл из хранилища (для отката).
     */
    public Mono<Void> deleteFile(String objectName) {
        return Mono.fromRunnable(() -> {
                    try {
                        minioClient.removeObject(
                                RemoveObjectArgs.builder()
                                        .bucket(bucketName)
                                        .object(objectName)
                                        .build()
                        );
                        log.info("Deleted file from storage: {}", objectName);
                    } catch (Exception e) {
                        log.error("Failed to delete file {} from storage", objectName, e);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private String generateObjectName(String originalFilename) {
        String extension = "";
        int lastDot = originalFilename.lastIndexOf('.');
        if (lastDot > 0) {
            extension = originalFilename.substring(lastDot);
        }
        return UUID.randomUUID() + extension;
    }
}