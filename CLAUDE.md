# Plataforma de Tutorías — Contexto del proyecto

## Este repositorio

Este repo es el **servicio de Sesiones (Java + Spring Boot)**. Aquí solo va código de Sesiones; el resto de la arquitectura es contexto.

- Tests: `./mvnw clean verify` (necesita Docker; coverage ≥ 60% con JaCoCo; sin `clean`, JaCoCo suma corridas anteriores)
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
- Código: `dominio/` (entidades, repositorios, `SesionesService` con todas las reglas, `Bloques`, puerto `ValidadorTutorRamo`), `grpc/` (adaptador del servidor), `kafka/` (publicador de eventos y `TopicosKafka`, que crea los tres tópicos al arrancar), `usuarios/` (`UsuariosGrpc`, adaptador gRPC de `ValidadorTutorRamo`, y `UsuariosMock`, que se usa solo sin `USUARIOS_GRPC_URL`) y `tareas/` (vencimiento programado, intervalo en `sesiones.vencimiento.intervalo`, por defecto `PT5M`).
- Stack: Spring Data JPA, servidor y cliente gRPC, Spring Kafka, tests con JUnit + Testcontainers (Postgres y Kafka reales).

## BFF (NestJS)

- Resolvers GraphQL que traducen a llamadas gRPC, eventos Kafka y REST al MCP.
- Mientras un servicio de un compañero no esté listo, usar mocks del cliente gRPC.
- Es el punto que define los contratos del sistema: cualquier cambio de contrato se coordina con el equipo.

## Contratos

- Viven en el repo `Contratos-TutorMatch`, incluido aquí como submódulo de git en `contracts/`. Clonar con `--recurse-submodules`; en GitHub Actions el checkout necesita `submodules: true`.
- Los contratos de Sesiones, Usuarios y Material están acordados (submódulo en `2fa0949`, "contratos acordados de Sesiones, Usuarios y Material"). El submódulo no se edita desde aquí: un cambio va por PR al repo de contratos y luego `git submodule update --remote contracts`.
- `contracts/proto/sesiones/v1/sesiones.proto`: lo implementa este servicio y lo consumen el BFF, Material y el agente. El build genera las clases Java desde ahí.
- `contracts/proto/usuarios/v1/usuarios.proto` (lo implementa Usuarios): Sesiones consume `ValidarTutorRamo` con `usuarios/UsuariosGrpc`; las clases Java generadas quedan en `cl.tutorias.usuarios.grpc`.
- `contracts/eventos/sesion.*.json`: ejemplos de los eventos que publica Sesiones y consume Material. JSON con `eventId`, `type`, `occurredAt` (ISO-8601 UTC) y `payload`; un tópico por `type` y la clave del mensaje es el `sesionId`. Los tópicos los crea Sesiones al arrancar (una partición, réplica 1): Material se suscribe sin crearlos y el broker puede tener la autocreación desactivada.
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

Estado de este repo al 2026-11-24, revisado contra el repo de contratos (commit `2fa0949`). El plan del BFF está en su propio `CLAUDE.md`.

1. ✅ Contratos: el `sesiones.proto` del submódulo es el que ya implementa el servicio (al moverlo solo cambió un comentario) y los eventos de `contracts/eventos/` coinciden con lo que publica `SesionesKafkaPublisher`. El traslado no deja cambios de código pendientes.
2. ✅ Servicio funcional: Flyway, JPA, los 8 RPC y el productor Kafka, probados a mano.
3. ✅ Decisiones del flujo implementadas según `propuestas/sesiones.md` (2026-11-16, rama `feat/casillas-disponibilidad`) y probadas a mano de punta a punta: migración V3, `MarcarCasilla`, `DesmarcarCasilla`, `ObtenerDisponibilidad` por rango con `libre`, `BuscarDisponibilidad`, filtros de `ListarSesiones`, validaciones de `CrearSesion` (casilla libre exacta, no reservarse a sí mismo, cruce entre papeles), solo el tutor confirma o finaliza, `VerificarParticipacion` con `tutor_id` y `ramo_codigo`, y la tarea programada de vencimiento. Mergeado en `main` (PR 5); el submódulo ya apunta a los contratos acordados.
4. ✅ Adaptador gRPC a `Usuarios.ValidarTutorRamo` (2026-11-17, rama `feat/cliente-usuarios`): dirección en `USUARIOS_GRPC_URL`, plazo de 3 s por llamada y `UNAVAILABLE` si Usuarios no responde; sin la variable se usa `UsuariosMock`. Probado a mano con un puerto sin servidor y con un Usuarios falso.
   - ✅ Probado contra el servidor real de Pablo el 2026-11-21 (repo `pablofgx-code/tutormatch-usuarios`, rama `main` en `ac11d53`, contratos en `2fa0949`), de punta a punta con GraphiQL → BFF → Sesiones: marcar casillas, reservar y confirmar, el rechazo de un ramo que la tutora no tiene, y quitar y reactivar el ramo. Se levantó con su Dockerfile (Postgres en el 55433, gRPC en el 50051) y Sesiones con `USUARIOS_GRPC_URL=localhost:50051`. Cómo repetirlo: README, "Probar contra Usuarios real".
   - Puerto: en local el servidor de Pablo escucha en el 50051 (variable `PORT`). En Kubernetes lo acordado es `usuarios:9090`, así que Pablo debe fijar `PORT=9090` en su manifiesto (o cambiar su valor por defecto).
5. Ya decidido por el contrato, no hacer nada:
   - No hace falta una tabla outbox: el contrato acepta que un evento se pierda y Material confirma con `VerificarParticipacion` antes de aceptar una evaluación.
   - Los ids son UUID en texto: las columnas `TEXT` de `tutor_id` y `estudiante_id` sirven tal cual.
6. ✅ Tests (etapa 2, 2026-11-17): unitarios de `Bloques` y `EstadoSesion`, e integración con Testcontainers (Postgres 17 y Kafka reales) para `SesionesService`, el adaptador gRPC y la publicación de eventos después del commit. JaCoCo excluye las clases generadas desde los `.proto` y hace fallar `verify` bajo 60% de líneas (hoy 93,9%). `UsuariosGrpc` no tiene test automático.
7. ✅ Dockerfile y CI (etapa 3, 2026-11-17): imagen multietapa (JDK 21 para compilar con `contracts/proto`, JRE 21 Alpine con usuario sin privilegios, puertos 9090 y 8080) probada en local; `.github/workflows/ci.yml` corre `./mvnw clean verify` con el submódulo y construye la imagen sin publicarla. La primera ejecución en GitHub pasó (2026-11-19).
   - ✅ Tópicos de Kafka (2026-11-23, rama `feat/topicos-kafka`): `kafka/TopicosKafka` crea `sesion.creada`, `sesion.finalizada` y `sesion.cancelada` al arrancar, porque Material (`IenZAVALA-S/tutormatch-material`) se suscribe con `allowAutoTopicCreation: false`. Los tests corren contra un Kafka sin autocreación de tópicos.
   - ✅ Probado contra el servicio real de Material el 2026-11-24 (repo `IenZAVALA-S/tutormatch-material`, `main` en `a61d82d`), con todo real salvo el agente (GraphiQL → BFF → servicios). Material corrió con gRPC en el 9095 y `KAFKA_BROKERS=localhost:9092`; Sesiones, con `DB_URL` en el 55432 y `USUARIOS_GRPC_URL=localhost:50051`. Resultados:
     - Los `sesion.creada` llegaron a Material (su consumidor, grupo `material`, lee desde el inicio e importó las sesiones existentes): un alumno con sesión con la tutora ve su material y otro sin sesiones no.
     - La tarea programada pasó a `FINALIZADA` una sesión confirmada y publicó `sesion.finalizada`.
     - `Material.Evaluar` llamó a `VerificarParticipacion` (con `tutor_id` y `ramo_codigo`) y aceptó la evaluación; repetirla dio `CONFLICT`.
     - Queda probado el ida y vuelta con los dos protocolos del curso: Sesiones → Material por Kafka y Material → Sesiones por gRPC.
   - En una prueba local, el `docker-compose` de Material trae su propio Kafka en el 9092, que choca con el de Sesiones: se usa solo el de Sesiones (`compose.yaml`) y se apunta Material a `localhost:9092`.
8. Siguiente (etapa 4), Kubernetes: Service `sesiones` con gRPC en el puerto 9090 y Actuator en 8080 para las probes; variables `DB_URL`, `DB_USER`, `DB_PASSWORD` (desde un Secret: el valor por defecto `sesiones` es solo para desarrollo), `KAFKA_BOOTSTRAP_SERVERS` y `USUARIOS_GRPC_URL=usuarios:9090` (requiere que Usuarios escuche en el 9090, ver punto 4). Antes hay que decidir dónde se publica la imagen.