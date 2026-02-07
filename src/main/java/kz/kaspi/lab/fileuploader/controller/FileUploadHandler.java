package kz.kaspi.lab.fileuploader.controller;

import kz.kaspi.lab.fileuploader.entity.Upload;
import kz.kaspi.lab.fileuploader.service.FileUploadService;
import kz.kaspi.lab.fileuploader.service.IdempotencyService;
import kz.kaspi.lab.fileuploader.exception.IdempotencyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileUploadHandler {

    private final FileUploadService fileUploadService;
    private final IdempotencyService idempotencyService;

    public Mono<ServerResponse> uploadFile(ServerRequest request) {
        String idempotencyKey = request.headers().firstHeader("X-Idempotency-Key");

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ServerResponse.badRequest()
                    .bodyValue(Map.of("error", "X-Idempotency-Key header is required"));
        }

        // Проверяем, не обработан ли уже такой ключ
        return idempotencyService.checkExistingStatus(idempotencyKey)
                .flatMap(existing -> handleExistingUpload(existing, idempotencyKey, request))
                .switchIfEmpty(processNewUpload(request, idempotencyKey))
                .onErrorResume(IdempotencyException.class, e ->
                        ServerResponse.status(HttpStatus.CONFLICT)
                                .bodyValue(Map.of("error", e.getMessage())))
                .onErrorResume(e -> {
                    log.error("Unexpected error during upload", e);
                    return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .bodyValue(Map.of("error", "Internal server error"));
                });
    }

    private Mono<ServerResponse> handleExistingUpload(Upload existing, String idempotencyKey, ServerRequest request) {
        if (existing.getStatus() == Upload.UploadStatus.SUCCESS) {
            return ServerResponse.status(HttpStatus.CONFLICT)
                    .bodyValue(Map.of(
                            "error", "Request already processed",
                            "uploadId", existing.getId(),
                            "status", existing.getStatus()
                    ));
        } else if (existing.getStatus() == Upload.UploadStatus.PROCESSING) {
            return ServerResponse.accepted()
                    .bodyValue(Map.of(
                            "message", "Request is being processed",
                            "uploadId", existing.getId()
                    ));
        } else {
            // FAILED — можно повторить, продолжаем обработку
            return processNewUpload(request, idempotencyKey);
        }
    }

    private Mono<ServerResponse> processNewUpload(ServerRequest request, String idempotencyKey) {
        return request.multipartData()
                .flatMap(parts -> {
                    Part filePart = parts.getFirst("file");
                    if (filePart == null) {
                        return ServerResponse.badRequest()
                                .bodyValue(Map.of("error", "File part 'file' is required"));
                    }

                    FilePart file = (FilePart) filePart;
                    Flux<DataBuffer> content = file.content();

                    // Неблокирующий запуск обработки
                    return fileUploadService.processUpload(
                            idempotencyKey,
                            file.filename(),
                            request.headers().contentLength().orElse(0L),
                            file.headers().getContentType() != null ?
                                    file.headers().getContentType().toString() :
                                    MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            content
                    ).flatMap(response -> ServerResponse.accepted()
                            .bodyValue(Map.of(
                                    "message", "File upload initiated",
                                    "uploadId", response.id(),
                                    "status", response.status()
                            ))
                    );
                });
    }
}
