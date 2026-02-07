package kz.kaspi.lab.fileuploader.dto;

import kz.kaspi.lab.fileuploader.entity.Upload.UploadStatus;

import java.time.Instant;
import java.util.UUID;

public record UploadResponse(
        UUID id,
        UploadStatus status,
        String originalFilename,
        String storagePath,
        Instant createdAt
) {}
