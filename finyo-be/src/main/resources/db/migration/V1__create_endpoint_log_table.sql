CREATE TABLE endpoint_log (
    id         BIGSERIAL    PRIMARY KEY,
    message    TEXT         NOT NULL,
    called_at  TIMESTAMPTZ  NOT NULL,
    endpoint   VARCHAR(255) NOT NULL
);
