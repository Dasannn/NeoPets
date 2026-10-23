CREATE TABLE np_pending_soul_delivery (
    token_id CHAR(36) NOT NULL,
    pet_id CHAR(36) NOT NULL,
    owner CHAR(36) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_np_pending_soul_delivery PRIMARY KEY (token_id)
);

CREATE INDEX idx_np_pending_soul_delivery_owner
    ON np_pending_soul_delivery (owner);
