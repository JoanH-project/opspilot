CREATE TABLE schema_marker (
    id BIGINT NOT NULL AUTO_INCREMENT,
    marker VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_schema_marker_marker (marker)
);

INSERT INTO schema_marker (marker) VALUES ('initial-schema');
