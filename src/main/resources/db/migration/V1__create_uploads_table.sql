CREATE TABLE uploads (
                         id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         idempotency_key VARCHAR(255) UNIQUE NOT NULL,
                         original_filename VARCHAR(500) NOT NULL,
                         storage_path VARCHAR(1000),
                         file_size BIGINT,
                         content_type VARCHAR(100),
                         status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
                         error_message TEXT,
                         created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_idempotency_key ON uploads(idempotency_key);
CREATE INDEX idx_status ON uploads(status);