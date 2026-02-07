package kz.kaspi.lab.fileuploader.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table("uploads")
public class Upload {

    @Id
    private UUID id;
    private String idempotencyKey;
    private String originalFilename;
    private String storagePath;
    private Long fileSize;
    private String contentType;
    private UploadStatus status;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;

    public enum UploadStatus {
        PROCESSING, SUCCESS, FAILED
    }
}
