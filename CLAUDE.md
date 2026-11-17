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
| Usuarios | Go | PostgreSQL | Pablo |
| Sesiones | Java + Spring Boot | PostgreSQL | **yo** |
| Material y evaluaciones | NestJS | MongoDB | Ien |
| MCP server + agente | TypeScript (SDK oficial MCP) | — | Pablo |

### Comunicación

- Frontend → BFF: GraphQL.
- BFF → Usuarios: gRPC.
- BFF → Sesiones: gRPC.
- BFF → Material: gRPC.
- BFF → MCP server: REST (`POST /agent/chat`).
- Sesiones → Usuarios: gRPC (`ValidarTutorRamo`: que el tutor existe y tiene el ramo activo).
- Sesiones → Material: Kafka (`sesion.creada`, `sesion.finalizada`, `sesion.cancelada`).
- Material → Sesiones: gRPC (verificar que la sesión finalizó y que el estudiante participó antes de aceptar una evaluación).

### Agente (MCP)

El MCP server expone tools que llaman a los servicios (buscar tutores, ver disponibilidad, crear reserva, generar quiz desde material). El loop del agente con el LLM en la nube vive en el mismo servicio. Caso de uso principal: "Necesito ayuda con Cálculo II el jueves en la tarde" → busca tutores, propone horarios y crea la reserva.

## Decisiones del flujo (acordadas el 2026-11-12)

Lo que afecta a Sesiones. La versión completa, con el flujo del front, está en el `CLAUDE.md` del BFF.

- **Un usuario puede ser alumno y tutor a la vez** (como en las ayudantías). Es tutor de un ramo cuando lo declaró aprobado. El JWT no lleva rol: el permiso por ramo lo valida Sesiones con `Usuarios.ValidarTutorRamo`, al crear una sesión y al marcar disponibilidad.
- **Disponibilidad semana a semana, por ramo:** el tutor marca casillas con fecha concreta para cada ramo. Una casilla no puede solaparse con otra del mismo tutor, sea del ramo que sea (`EXCLUDE` sobre `tutor_id` y el rango, como en `sesiones`).
- **Casillas = bloques de clase de la universidad** (acordado el 2026-11-16): 90 minutos, solo de lunes a viernes (no hay tutorías los fines de semana, confirmado el 2026-11-17), con inicio fijo en hora de Chile a las 08:10, 09:55, 11:40, 14:30, 16:15 y 18:00 (entre el tercero y el cuarto está el almuerzo). Una sesión ocupa exactamente una casilla. Sesiones valida el bloque con la zona `America/Santiago` (`dominio/Bloques`). Detalle completo en `propuestas/sesiones.md` del repo de contratos.
- **Solo el tutor confirma o finaliza una sesión;** el alumno solo puede cancelarla.
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
- Tablas: `sesiones` (id, tutor_id, estudiante_id, ramo_codigo, inicio, fin, horario generado, estado, creada_en) y `disponibilidad` (casillas: id, tutor_id, ramo_codigo, inicio, fin, horario generado). La V3 agregó el `EXCLUDE` de casillas, un `CHECK` de 90 minutos, `sesiones_no_a_si_mismo` y `sesiones_estudiante_sin_choque`.
- Código: `dominio/` (entidades, repositorios, `SesionesService` con todas las reglas, `Bloques`, puerto `ValidadorTutorRamo`), `grpc/` (adaptador del servidor), `kafka/` (publicador de eventos), `usuarios/` (mock de `ValidadorTutorRamo`) y `tareas/` (vencimiento programado, intervalo en `sesiones.vencimiento.intervalo`, por defecto `PT5M`).
- Stack: Spring Data JPA, servidor y cliente gRPC, Spring Kafka, tests con JUnit + Testcontainers (Postgres y Kafka reales).

## BFF (NestJS)

- Resolvers GraphQL que traducen a llamadas gRPC, eventos Kafka y REST al MCP.
- Mientras un servicio de un compañero no esté listo, usar mocks del cliente gRPC.
- Es el punto que define los contratos del sistema: cualquier cambio de contrato se coordina con el equipo.

## Contratos

- Viven en el repo `Contratos-TutorMatch`, incluido aquí como submódulo de git en `contracts/`. Clonar con `--recurse-submodules`; en GitHub Actions el checkout necesita `submodules: true`.
- Los contratos de Sesiones, Usuarios y Material están acordados (submódulo en `2fa0949`, "contratos acordados de Sesiones, Usuarios y Material"). El submódulo no se edita desde aquí: un cambio va por PR al repo de contratos y luego `git submodule update --remote contracts`.
- `contracts/proto/sesiones/v1/sesiones.proto`: lo implementa este servicio y lo consumen el BFF, Material y el agente. El build genera las clases Java desde ahí.
- `contracts/proto/usuarios/v1/usuarios.proto` (lo implementa Usuarios): Sesiones consume `ValidarTutorRamo`, cuyas clases Java quedan en `cl.tutorias.usuarios.grpc`. Mientras no exista el adaptador gRPC, se usa `usuarios/UsuariosMock`.
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

Estado de este repo al 2026-11-17, revisado contra el repo de contratos (commit `2fa0949`). El plan del BFF está en su propio `CLAUDE.md`.

1. ✅ Contratos: el `sesiones.proto` del submódulo es el que ya implementa el servicio (al moverlo solo cambió un comentario) y los eventos de `contracts/eventos/` coinciden con lo que publica `SesionesKafkaPublisher`. El traslado no deja cambios de código pendientes.
2. ✅ Servicio funcional: Flyway, JPA, los 8 RPC y el productor Kafka, probados a mano.
3. ✅ Decisiones del flujo implementadas según `propuestas/sesiones.md` (2026-11-16, rama `feat/casillas-disponibilidad`) y probadas a mano de punta a punta: migración V3, `MarcarCasilla`, `DesmarcarCasilla`, `ObtenerDisponibilidad` por rango con `libre`, `BuscarDisponibilidad`, filtros de `ListarSesiones`, validaciones de `CrearSesion` (casilla libre exacta, no reservarse a sí mismo, cruce entre papeles), solo el tutor confirma o finaliza, `VerificarParticipacion` con `tutor_id` y `ramo_codigo`, y la tarea programada de vencimiento. Mergeado en `main` (PR 5); el submódulo ya apunta a los contratos acordados.
4. Siguiente: adaptador gRPC a `Usuarios.ValidarTutorRamo`, ya desbloqueado porque `usuarios.proto` está en `contracts/proto`. Agregar `contracts/proto/usuarios/v1` a los `sourceDirectories` del plugin de protobuf, el starter del cliente gRPC y la dirección en `USUARIOS_GRPC_URL`; usar `UsuariosMock` solo sin esa variable; si Usuarios no responde, `UNAVAILABLE`. Hoy el mock acepta cualquier tutor y ramo.
5. Ya decidido por el contrato, no hacer nada:
   - No hace falta una tabla outbox: el contrato acepta que un evento se pierda y Material confirma con `VerificarParticipacion` antes de aceptar una evaluación.
   - Los ids son UUID en texto: las columnas `TEXT` de `tutor_id` y `estudiante_id` sirven tal cual.
6. Después (etapa 2): tests hasta coverage ≥ 60%.
7. Después (etapa 3), Dockerfile y pipeline: el build necesita la carpeta `contracts/` (checkout con `submodules: true` y copiarla a la imagen donde se compila).
8. Kubernetes: Service `sesiones` con gRPC en el puerto 9090, y las variables `USUARIOS_GRPC_URL` y `KAFKA_BOOTSTRAP_SERVERS`.