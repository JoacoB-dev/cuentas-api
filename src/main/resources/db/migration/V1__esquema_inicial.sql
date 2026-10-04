-- Esquema inicial de la API de cuentas y transferencias.
-- Los importes se guardan como NUMERIC(19,2): nunca float para plata.

CREATE TABLE cliente (
    id          BIGSERIAL PRIMARY KEY,
    nombre      VARCHAR(80)  NOT NULL,
    apellido    VARCHAR(80)  NOT NULL,
    dni         VARCHAR(8)   NOT NULL UNIQUE,
    email       VARCHAR(120) NOT NULL,
    creado_en   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE usuario (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(60)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    rol           VARCHAR(20)  NOT NULL CHECK (rol IN ('CLIENTE', 'OPERADOR')),
    cliente_id    BIGINT REFERENCES cliente (id),
    -- Un usuario CLIENTE siempre está asociado a un cliente; un OPERADOR no.
    CONSTRAINT usuario_cliente_rol CHECK (
        (rol = 'CLIENTE' AND cliente_id IS NOT NULL) OR (rol = 'OPERADOR' AND cliente_id IS NULL)
    )
);

-- Número de cuenta interno (13 dígitos dentro del bloque 2 del CBU).
CREATE SEQUENCE cuenta_numero_seq START WITH 1000;

CREATE TABLE cuenta (
    id                     BIGSERIAL PRIMARY KEY,
    cliente_id             BIGINT        NOT NULL REFERENCES cliente (id),
    cbu                    VARCHAR(22)   NOT NULL UNIQUE,
    tipo                   VARCHAR(2)    NOT NULL CHECK (tipo IN ('CA', 'CC')),
    moneda                 VARCHAR(3)    NOT NULL CHECK (moneda IN ('ARS', 'USD')),
    estado                 VARCHAR(10)   NOT NULL CHECK (estado IN ('ACTIVA', 'BLOQUEADA')),
    saldo                  NUMERIC(19,2) NOT NULL,
    descubierto_autorizado NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (descubierto_autorizado >= 0),
    limite_diario          NUMERIC(19,2) NOT NULL CHECK (limite_diario > 0),
    creada_en              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- Una caja de ahorro no puede tener descubierto.
    CONSTRAINT cuenta_ca_sin_descubierto CHECK (tipo = 'CC' OR descubierto_autorizado = 0),
    -- Red de seguridad a nivel base: el saldo nunca baja del descubierto acordado.
    CONSTRAINT cuenta_saldo_minimo CHECK (saldo >= -descubierto_autorizado)
);
CREATE INDEX idx_cuenta_cliente ON cuenta (cliente_id);

CREATE TABLE transferencia (
    id                BIGSERIAL PRIMARY KEY,
    cuenta_origen_id  BIGINT        NOT NULL REFERENCES cuenta (id),
    cuenta_destino_id BIGINT        NOT NULL REFERENCES cuenta (id),
    importe           NUMERIC(19,2) NOT NULL CHECK (importe > 0),
    moneda            VARCHAR(3)    NOT NULL,
    concepto          VARCHAR(140),
    usuario           VARCHAR(60)   NOT NULL,
    idempotency_key   VARCHAR(100)  NOT NULL,
    hash_solicitud    VARCHAR(64)   NOT NULL,
    fecha             TIMESTAMPTZ   NOT NULL,
    CONSTRAINT transferencia_cuentas_distintas CHECK (cuenta_origen_id <> cuenta_destino_id),
    -- La misma clave de idempotencia sólo puede usarse una vez por usuario.
    CONSTRAINT uq_transferencia_idempotencia UNIQUE (usuario, idempotency_key)
);
-- Para calcular rápido lo transferido en el día (límite diario).
CREATE INDEX idx_transferencia_origen_fecha ON transferencia (cuenta_origen_id, fecha);

CREATE TABLE movimiento (
    id               BIGSERIAL PRIMARY KEY,
    cuenta_id        BIGINT        NOT NULL REFERENCES cuenta (id),
    tipo             VARCHAR(7)    NOT NULL CHECK (tipo IN ('DEBITO', 'CREDITO')),
    importe          NUMERIC(19,2) NOT NULL CHECK (importe > 0),
    saldo_posterior  NUMERIC(19,2) NOT NULL,
    descripcion      VARCHAR(160)  NOT NULL,
    transferencia_id BIGINT REFERENCES transferencia (id),
    fecha            TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_movimiento_cuenta_fecha ON movimiento (cuenta_id, fecha, id);
