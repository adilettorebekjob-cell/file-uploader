# File Uploader

Spring Boot (WebFlux) сервис для загрузки файлов с идемпотентностью и асинхронной обработкой.

## Запуск
docker-compose up -d

API
curl -X POST http://localhost:8080/api/v1/upload \
  -H "X-Idempotency-Key: unique-key" \
  -F "file=@document.pdf"
202 Accepted — файл в обработке
409 Conflict — дубликат (идемпотентность)
