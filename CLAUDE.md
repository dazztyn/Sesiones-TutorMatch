# Plataforma de Tutorías — Contexto del proyecto

## Este repositorio

Este repo es el **servicio de Sesiones (Java + Spring Boot)**. Aquí solo va código de Sesiones; el resto de la arquitectura es contexto.

- Tests: `./mvnw verify` (coverage ≥ 60% con JaCoCo)
- Levantar en local: `./mvnw spring-boot:run`

Proyecto 2 del curso de Arquitectura de Sistemas. Plataforma donde estudiantes reservan tutorías con otros estudiantes (tutores) que aprobaron el ramo. Arquitectura de microservicios en Kubernetes.

## Requisitos del curso (no negociables)

- Frontend → BFF por **GraphQL** (patrón Backend for Frontend, actúa como API Gateway).
- Al menos **2 protocolos** entre microservicios: usamos **gRPC** y **Kafka**.
- Los microservicios se comunican entre sí en ambos sentidos (ej. A → B por Kafka, B → A por gRPC).
- **Cada microservicio tiene su propia base de datos** (los motores pueden repetirse).
- Cuarto servicio: **MCP server** conectado a un modelo de IA en la nube, que funciona como agente. Se comunica con el BFF por **REST**.
- **Prohibido usar frameworks de Python** (ya se usó FastAPI en el proyecto 1).
- Cada servicio en **su propio repositorio**, dockerizado, con **pipeline de CI** (GitHub Actions).
- Todo el sistema desplegado en un **cluster de Kubernetes**.
- **Coverage mínimo 60%** (el pipeline debe fallar bajo ese umbral).

## Arquitectura

| Componente | Tecnología | Base de datos | Responsable |
|---|---|---|---|
| Frontend | por definir | — | compañero |
| BFF / API Gateway | NestJS (GraphQL) | — | **yo** |
| Usuarios | Go (por confirmar) | PostgreSQL | compañero |
| Sesiones | Java + Spring Boot | PostgreSQL | **yo** |
| Material y evaluaciones | .NET (por confirmar) | MongoDB | compañero |
| MCP server + agente | TypeScript (SDK oficial MCP) | — | compañero |

### Comunicación

- Frontend → BFF: GraphQL.
- BFF → Usuarios: gRPC.
- BFF → Sesiones: gRPC.
- BFF → Material: Kafka (comandos/eventos).
- BFF → MCP server: REST (`POST /agent/chat`).
- Sesiones → Usuarios: gRPC (validar que el tutor existe y aprobó el ramo).
- Sesiones → Material: Kafka (`sesion.creada`, `sesion.finalizada`, `sesion.cancelada`).
- Material → Sesiones: gRPC (verificar que la sesión finalizó y que el estudiante participó antes de aceptar una evaluación).

### Agente (MCP)

El MCP server expone tools que llaman a los servicios (buscar tutores, ver disponibilidad, crear reserva, generar quiz desde material). El loop del agente con el LLM en la nube vive en el mismo servicio. Caso de uso principal: "Necesito ayuda con Cálculo II el jueves en la tarde" → busca tutores, propone horarios y crea la reserva.

## Decisiones del flujo (acordadas el 2026-11-12)

Lo que afecta a Sesiones. La versión completa, con el flujo del front, está en el `CLAUDE.md` del BFF.

- **Un usuario puede ser alumno y tutor a la vez** (como en las ayudantías). Es tutor de un ramo cuando lo declaró aprobado. El JWT no lleva rol: el permiso por ramo lo valida Sesiones con `Usuarios.VerificarTutorRamo`, al crear una sesión y al marcar disponibilidad.
- **Disponibilidad semana a semana, por ramo:** el tutor marca casillas con fecha concreta para cada ramo. Una casilla no puede solaparse con otra del mismo tutor, sea del ramo que sea (`EXCLUDE` sobre `tutor_id` y el rango, como en `sesiones`). Reemplaza el patrón semanal actual (`dia`, `hora_ini`, `hora_fin`, sin ramo). Falta definir la duración de una casilla.
- **Reservar:** solo se puede tomar una casilla disponible de ese tutor y ese ramo; `CrearSesion` lo valida. Nadie puede reservarse a sí mismo, y un usuario no puede tener dos sesiones que se crucen, aunque en una sea alumno y en la otra tutor.
- **Confirmación:** la reserva queda `PENDIENTE` hasta que el tutor la confirme; rechazar es cancelar. Mientras está pendiente el horario queda bloqueado (el `EXCLUDE` actual de `sesiones` ya lo cubre).
- **Desmarcar:** una casilla con una sesión `PENDIENTE` o `CONFIRMADA` no se puede desmarcar (`FAILED_PRECONDITION`); primero se cancela la sesión.
- **Sin websockets:** el front del tutor consulta cada 30 a 60 segundos. Sesiones solo necesita que `ListarSesiones` filtre por estado y por rango de fechas (para la vista semanal).
- **Fin y vencimiento automáticos:** una tarea programada en Sesiones, cada pocos minutos:
  - Una sesión `CONFIRMADA` cuyo `fin` ya pasó pasa a `FINALIZADA` y publica `sesion.finalizada`.
  - Una sesión `PENDIENTE` cuyo `inicio` ya pasó sin confirmación pasa a `CANCELADA` y publica `sesion.cancelada`.
  - Así el alumno siempre puede evaluar: Material solo acepta una evaluación si `VerificarParticipacion` dice que la sesión está `FINALIZADA` y que el alumno participó.
- **Zona horaria:** todo en UTC (base de datos y contratos). La disponibilidad deja de usar "HH:mm".

## Servicio de Sesiones (Spring Boot)

- Responsable de agenda, disponibilidad de tutores y ciclo de vida de una sesión: `PENDIENTE → CONFIRMADA → FINALIZADA` o `CANCELADA`.
- PostgreSQL con migraciones Flyway. Los choques de horario de un tutor se impiden en la base de datos con un `EXCLUDE USING gist (tutor_id WITH =, horario WITH &&)` sobre un `TSTZRANGE` (requiere `btree_gist`), aplicado solo a sesiones `PENDIENTE` o `CONFIRMADA`.
- Tablas iniciales: `sesiones` (id, tutor_id, estudiante_id, ramo_codigo, horario, estado, creada_en) y `disponibilidad` (id, tutor_id, dia, hora_ini, hora_fin). La migración V3 cambiará `disponibilidad` a casillas con fecha concreta y ramo (ver "Decisiones del flujo").
- Stack: Spring Data JPA, servidor y cliente gRPC, Spring Kafka, tests con JUnit + Testcontainers (Postgres y Kafka reales).

## BFF (NestJS)

- Resolvers GraphQL que traducen a llamadas gRPC, eventos Kafka y REST al MCP.
- Mientras un servicio de un compañero no esté listo, usar mocks del cliente gRPC.
- Es el punto que define los contratos del sistema: cualquier cambio de contrato se coordina con el equipo.

## Contratos

- Viven en el repo `Contratos-TutorMatch`, incluido aquí como submódulo de git en `contracts/`. Clonar con `--recurse-submodules`; en GitHub Actions el checkout necesita `submodules: true`.
- `contracts/proto/sesiones/v1/sesiones.proto`: lo implementa este servicio y lo consumen el BFF, Material y el agente. El build genera las clases Java desde ahí.
- `contracts/proto/usuarios/v1/usuarios.proto` (borrador, lo implementa Usuarios): Sesiones consume `VerificarTutorRamo`.
- `contracts/eventos/sesion.*.json`: ejemplos de los eventos que publica Sesiones y consume Material. JSON con `eventId`, `type`, `occurredAt` (ISO-8601 UTC) y `payload`; un tópico por `type` y la clave del mensaje es el `sesionId`.
- Ids de usuario y de sesión: UUID en texto. Códigos de ramo: los del catálogo de `Usuarios.ListarRamos` (ej. `MAT1610`).
- Direcciones gRPC en Kubernetes: `<servicio>:9090`, leídas de variables como `USUARIOS_GRPC_URL`.
- Cambiar un contrato: PR en el repo de contratos y avisar a quienes lo consumen. Agregar campos con números nuevos es seguro; nunca reutilizar ni cambiar un número; renombrar un RPC o cambiar un tipo exige crear `v2`.

## Convenciones

- Comentarios y documentación en español.
- Cada repo: `Dockerfile`, `.github/workflows/ci.yml` con tests + coverage ≥ 60%, y manifiestos de Kubernetes.

## Flujo de trabajo con git

- Puedes crear ramas y cambiarte entre ellas (`git checkout -b`, `git switch`).
- Aplica los cambios **solo en local**: edita archivos, pero **nunca hagas `git commit`, `git push`, `git merge` ni `git rebase`**.
- Yo reviso los cambios en el IDE y hago los commits y el push.
- Al terminar una tarea, resume qué archivos cambiaste y propón un mensaje de commit en español, para que yo lo use si me sirve.

## Orden de desarrollo

Primero un servicio **funcional**, después la calidad:

1. **Funcionalidad:** que el servicio levante, se conecte a su base de datos y responda sus endpoints o RPC de punta a punta. Probar a mano (grpcurl, GraphQL Playground, Postman).
2. **Tests:** recién cuando lo funcional esté estable, escribir tests unitarios y de integración hasta superar el 60% de coverage.
3. **Docker y pipeline:** `Dockerfile` y workflow de GitHub Actions con el umbral de coverage.
4. **Kubernetes:** manifiestos y despliegue en el cluster.

Mientras estemos en la etapa 1, no escribas tests ni configures el pipeline salvo que te lo pida, pero escribe el código de forma testeable (inyección de dependencias, lógica separada de los adaptadores gRPC/Kafka/DB).

## Próximos pasos

Estado de este repo al 2026-11-12, revisado contra el repo de contratos (commit `9a670f7`). El plan del BFF está en su propio `CLAUDE.md`.

1. ✅ Contratos: el `sesiones.proto` del submódulo es el que ya implementa el servicio (al moverlo solo cambió un comentario) y los eventos de `contracts/eventos/` coinciden con lo que publica `SesionesKafkaPublisher`. El traslado no deja cambios de código pendientes.
2. ✅ Servicio funcional: Flyway, JPA, los 8 RPC y el productor Kafka, probados a mano.
3. Pendiente, en este orden (ver "Decisiones del flujo"):
   1. Cliente gRPC a `Usuarios.VerificarTutorRamo(tutor_id, ramo_codigo)`, usado al crear una sesión y al marcar disponibilidad: responde `FAILED_PRECONDITION` si `existe` o `aprobo_ramo` es falso (ese código ya está documentado en `sesiones.proto`). Tener en cuenta:
      - `usuarios.proto` es un borrador y el servicio de Usuarios todavía no existe: el cliente va detrás de una interfaz del dominio, con un mock mientras tanto.
      - Hay que agregar `contracts/proto/usuarios/v1` a los `sourceDirectories` del plugin de protobuf y el starter del cliente gRPC, y leer la dirección de `USUARIOS_GRPC_URL`.
      - `usuarios.proto` no declara `java_package`: las clases Java quedan en el paquete `tutorias.usuarios.v1`, anidadas en la clase `Usuarios`. Si molesta, proponer las opciones de Java en un PR al repo de contratos.
   2. Migración V3: `disponibilidad` con fecha concreta, ramo y `EXCLUDE` contra solapes del mismo tutor. Depende de definir la duración de una casilla.
   3. RPC nuevos de disponibilidad (marcar, desmarcar, leer por rango) y filtros de estado y fechas en `ListarSesiones`, una vez acordado el cambio de contrato.
   4. Validaciones al crear: casilla disponible de ese tutor y ramo, no reservarse a sí mismo, sin cruce con otras sesiones del usuario como alumno o tutor. El `EXCLUDE` actual de `sesiones` solo impide choques por `tutor_id`, así que el cruce en cualquier rol necesita una validación aparte.
   5. Desmarcar falla con `FAILED_PRECONDITION` si hay una sesión `PENDIENTE` o `CONFIRMADA` en ese horario.
   6. Tarea programada: finalizar las confirmadas vencidas y cancelar las pendientes no confirmadas a tiempo, publicando sus eventos.
4. Cambio de contrato pendiente, lo propone Vicente en el repo de contratos (no hacerlo desde aquí): `BloqueDisponibilidad` con `ramo_codigo`, `inicio` y `fin` (`Timestamp`) en lugar de `dia`, `hora_ini` y `hora_fin`; RPC para marcar y desmarcar; `ObtenerDisponibilidad` por rango; `ListarSesiones` con filtros de estado y fechas. El README de contratos pide `v2` para cambios de tipo, pero se propondrá cambiar `v1` con el visto bueno del equipo, porque el único otro consumidor es el agente, que sigue en borrador.
5. Ya decidido por el contrato, no hacer nada:
   - No hace falta una tabla outbox: el contrato acepta que un evento se pierda y Material confirma con `VerificarParticipacion` antes de aceptar una evaluación.
   - Los ids son UUID en texto: las columnas `TEXT` de `tutor_id` y `estudiante_id` sirven tal cual.
6. Tests hasta coverage ≥ 60%.
7. Dockerfile y pipeline: el build necesita la carpeta `contracts/` (checkout con `submodules: true` y copiarla a la imagen donde se compila).
8. Kubernetes: Service `sesiones` con gRPC en el puerto 9090, y las variables `USUARIOS_GRPC_URL` y `KAFKA_BOOTSTRAP_SERVERS`.