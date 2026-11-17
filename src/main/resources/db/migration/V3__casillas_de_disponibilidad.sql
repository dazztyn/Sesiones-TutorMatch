-- La disponibilidad pasa de un patrón semanal sin ramo a casillas con fecha y ramo.
-- La tabla anterior solo tenía datos de prueba.
DROP TABLE disponibilidad;

CREATE TABLE disponibilidad (
    id          UUID        PRIMARY KEY,
    tutor_id    TEXT        NOT NULL,
    ramo_codigo TEXT        NOT NULL,
    inicio      TIMESTAMPTZ NOT NULL,
    fin         TIMESTAMPTZ NOT NULL,
    horario     TSTZRANGE   NOT NULL GENERATED ALWAYS AS (tstzrange(inicio, fin, '[)')) STORED,
    -- Una casilla es un bloque de clase. Que empiece en un bloque válido (hora de Chile)
    -- lo valida el servicio, porque depende de la zona horaria.
    CHECK (fin = inicio + interval '90 minutes'),
    -- Un tutor no puede tener dos casillas que se solapen, aunque sean de ramos distintos.
    CONSTRAINT disponibilidad_sin_solape
        EXCLUDE USING gist (tutor_id WITH =, horario WITH &&)
);

-- Nadie se reserva a sí mismo.
ALTER TABLE sesiones ADD CONSTRAINT sesiones_no_a_si_mismo CHECK (tutor_id <> estudiante_id);

-- Un alumno no puede tener dos sesiones activas que se crucen (la de los tutores ya existe en V2).
ALTER TABLE sesiones ADD CONSTRAINT sesiones_estudiante_sin_choque
    EXCLUDE USING gist (estudiante_id WITH =, horario WITH &&)
    WHERE (estado IN ('PENDIENTE', 'CONFIRMADA'));
