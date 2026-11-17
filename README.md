# Sesiones-TutorMatch

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
`GRPC_PORT`). El contrato está en `contracts/proto/sesiones/v1/sesiones.proto`, dentro del
submódulo de contratos: al clonar hay que usar `git clone --recurse-submodules`, o ejecutar
`git submodule update --init` si el repo ya está clonado.

El servidor tiene reflexión activada, así que con [grpcurl](https://github.com/fullstorydev/grpcurl)
se pueden listar y llamar los RPC sin el `.proto`:

```bash
grpcurl -plaintext localhost:9090 list tutorias.sesiones.v1.SesionesService
```

### Disponibilidad y reservas

- El tutor marca **casillas**: bloques de clase de 90 minutos, de lunes a viernes, que empiezan a
  las 08:10, 09:55, 11:40, 14:30, 16:15 o 18:00, hora de Chile (`America/Santiago`). Los
  `Timestamp` viajan en UTC; el servicio convierte solo para validar.
- Una reserva (`CrearSesion`) tiene que coincidir con una casilla libre de ese tutor, ese ramo y
  ese horario exacto.
- Antes de marcar una casilla o crear una reserva, el servicio pregunta a Usuarios si el tutor tiene
  ese ramo activo (`Usuarios.ValidarTutorRamo` por gRPC). La dirección se lee de
  `USUARIOS_GRPC_URL` (ej. `localhost:50051` en local, `usuarios:9090` en Kubernetes). Cada llamada
  espera como máximo 3 s; si Usuarios no responde, el RPC de Sesiones falla con `UNAVAILABLE`.
- Sin `USUARIOS_GRPC_URL`, responde un mock que siempre dice que sí (`usuarios/UsuariosMock`), para
  trabajar en local sin el servicio de Usuarios.

### Tarea programada

Cada 5 minutos el servicio finaliza las sesiones confirmadas que ya terminaron y cancela las
pendientes cuyo inicio pasó sin confirmación, publicando sus eventos. Para probarlo en local se
puede acortar el intervalo:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--sesiones.vencimiento.intervalo=PT10S
```

### Puerto 5432 ocupado

Si otro proyecto ya usa el puerto 5432, `spring-boot:run` falla al levantar `compose.yaml`. Se
puede levantar Postgres en otro puerto con el mismo volumen y apagar la integración con compose:

```bash
docker compose up -d kafka
docker run -d --name sesiones-pg -p 55432:5432 -e POSTGRES_DB=sesiones -e POSTGRES_USER=sesiones -e POSTGRES_PASSWORD=sesiones -v sesionesarq2_sesiones-pgdata:/var/lib/postgresql/data postgres:17-alpine
DB_URL=jdbc:postgresql://localhost:55432/sesiones ./mvnw spring-boot:run -Dspring-boot.run.arguments=--spring.docker.compose.enabled=false
```

## Eventos Kafka

El servicio publica un evento cuando una sesión se crea, se finaliza o se cancela
(confirmarla no publica nada). El broker se configura con `KAFKA_BOOTSTRAP_SERVERS`
(por defecto `localhost:9092`).

| Tópico y `type`     | Cuándo                         |
|---------------------|--------------------------------|
| `sesion.creada`     | `CrearSesion` guardó la sesión |
| `sesion.finalizada` | `FinalizarSesion`, o la tarea programada al terminar una sesión confirmada |
| `sesion.cancelada`  | `CancelarSesion`, o la tarea programada al vencer una sesión pendiente |

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
