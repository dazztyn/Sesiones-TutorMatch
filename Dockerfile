# Imagen del servicio de Sesiones. Se construye desde la raíz del repo, con el submódulo
# contracts/ descargado (git clone --recurse-submodules):
#   docker build -t sesiones .
# La base de datos, Kafka y Usuarios llegan por variables de entorno (ver README).

# ---- Etapa 1: compilar el jar -------------------------------------------------------------
# JDK completo sobre Ubuntu: el plugin de protobuf descarga protoc, que no corre en Alpine.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

COPY mvnw pom.xml ./
COPY .mvn .mvn
# El build genera las clases gRPC desde los .proto del submódulo de contratos.
COPY contracts/proto contracts/proto
COPY src src

# Los tests corren en CI (necesitan Docker); aquí solo se empaqueta. La caché guarda las
# dependencias de Maven entre builds.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
    && cp target/sesiones-*.jar app.jar

# ---- Etapa 2: imagen final, solo con el JRE -----------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S sesiones && adduser -S -G sesiones sesiones
COPY --from=build /build/app.jar app.jar
USER sesiones

# 9090: gRPC (la API del servicio). 8080: Actuator (/actuator/health para las probes).
EXPOSE 9090 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
