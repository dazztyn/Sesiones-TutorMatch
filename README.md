# SesionesArq2

Servicio de **Sesiones** de la plataforma de tutorías (Java 21 + Spring Boot 4).
Maneja la agenda, la disponibilidad de tutores y el ciclo de vida de las sesiones.

## Requisitos

- JDK 21
- Docker (Docker Desktop en Windows/macOS)
- No hace falta instalar Maven: el proyecto trae el wrapper `./mvnw`.

## Levantar en local

```bash
./mvnw spring-boot:run
```

Spring Boot levanta solo el PostgreSQL y el Kafka de `compose.yaml`, aplica las migraciones de
Flyway (`src/main/resources/db/migration`) y arranca el servicio.

Comprobar que está vivo y conectado a la base de datos:

```bash
curl http://localhost:8080/actuator/health
```

## API gRPC

El servicio expone `tutorias.sesiones.v1.SesionesService` en el puerto `9090` (variable
`GRPC_PORT`). El contrato está en `src/main/protobuf/sesiones.proto`; es una copia del
que vive en el BFF, y hay que mantener las dos iguales hasta que exista el repo `contracts`.

El servidor tiene reflexión activada, así que con [grpcurl](https://github.com/fullstorydev/grpcurl)
se pueden listar y llamar los RPC sin el `.proto`:

```bash
grpcurl -plaintext localhost:9090 list tutorias.sesiones.v1.SesionesService
```

## Eventos Kafka

El servicio publica un evento cuando una sesión se crea, se finaliza o se cancela
(confirmarla no publica nada). El broker se configura con `KAFKA_BOOTSTRAP_SERVERS`
(por defecto `localhost:9092`).

| Tópico y `type`     | Cuándo                         |
|---------------------|--------------------------------|
| `sesion.creada`     | `CrearSesion` guardó la sesión |
| `sesion.finalizada` | `FinalizarSesion`              |
| `sesion.cancelada`  | `CancelarSesion`               |

La clave del mensaje es el id de la sesión y el valor es JSON:

```json
{
  "eventId": "16a6ef9b-043c-4070-8c5f-45b9c31be19a",
  "type": "sesion.creada",
  "occurredAt": "2026-11-12T03:12:56.869681800Z",
  "payload": {
    "sesionId": "2efc9591-b813-4d38-8004-2777228e00ac",
    "tutorId": "...",
    "estudianteId": "...",
    "ramoCodigo": "MAT1620",
    "inicio": "2027-01-10T15:00:00Z",
    "fin": "2027-01-10T16:00:00Z"
  }
}
```

El evento se envía después de guardar la sesión. Si Kafka no responde, la sesión queda
guardada igual y el evento se pierde (queda el error en el log).

Para ver los mensajes en local:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --include 'sesion\..*' --from-beginning
```

## Tests

```bash
./mvnw verify
```
