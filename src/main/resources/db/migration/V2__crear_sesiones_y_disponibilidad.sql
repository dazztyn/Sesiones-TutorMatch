-- tutor_id y estudiante_id son TEXT porque el formato de los ids lo define el
-- servicio de Usuarios, que todavía no existe.
CREATE TABLE sesiones (
    id            UUID        PRIMARY KEY,
    tutor_id      TEXT        NOT NULL,
    estudiante_id TEXT        NOT NULL,
    ramo_codigo   TEXT        NOT NULL,
    inicio        TIMESTAMPTZ NOT NULL,
    fin           TIMESTAMPTZ NOT NULL,
    -- Rango [inicio, fin) calculado por la base; JPA solo escribe inicio y fin.
    horario       TSTZRANGE   NOT NULL GENERATED ALWAYS AS (tstzrange(inicio, fin, '[)')) STORED,
    estado        TEXT        NOT NULL
        CHECK (estado IN ('PENDIENTE', 'CONFIRMADA', 'FINALIZADA', 'CANCELADA')),
    creada_en     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (fin > inicio),
    -- Un tutor no puede tener dos sesiones activas que se solapen. Las canceladas
    -- y finalizadas no bloquean el horario.
    CONSTRAINT sesiones_sin_choque
        EXCLUDE USING gist (tutor_id WITH =, horario WITH &&)
        WHERE (estado IN ('PENDIENTE', 'CONFIRMADA'))
);

CREATE TABLE disponibilidad (
    id       BIGINT   GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tutor_id TEXT     NOT NULL,
    -- Día de la semana ISO: 1 = lunes ... 7 = domingo.
    dia      SMALLINT NOT NULL CHECK (dia BETWEEN 1 AND 7),
    hora_ini TIME     NOT NULL,
    hora_fin TIME     NOT NULL,
    CHECK (hora_fin > hora_ini)
);
