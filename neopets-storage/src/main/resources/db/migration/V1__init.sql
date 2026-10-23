CREATE TABLE np_player (
    uuid CHAR(36) NOT NULL,
    sp_balance INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_np_player PRIMARY KEY (uuid)
);

CREATE TABLE np_pet (
    id CHAR(36) NOT NULL,
    owner CHAR(36) NOT NULL,
    variant_id VARCHAR(128) NOT NULL,
    name VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_np_pet PRIMARY KEY (id),
    CONSTRAINT uq_np_pet_owner_variant UNIQUE (owner, variant_id)
);

CREATE INDEX idx_np_pet_owner ON np_pet (owner);

CREATE TABLE np_pet_attribute (
    pet_id CHAR(36) NOT NULL,
    attribute_id VARCHAR(128) NOT NULL,
    unlocked_level INTEGER NOT NULL DEFAULT 0,
    active_level INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_np_pet_attribute PRIMARY KEY (pet_id, attribute_id),
    CONSTRAINT fk_np_pet_attribute_pet FOREIGN KEY (pet_id)
        REFERENCES np_pet (id) ON DELETE CASCADE
);

CREATE TABLE np_soul_token (
    token_id CHAR(36) NOT NULL,
    pet_id CHAR(36) NOT NULL,
    owner CHAR(36) NOT NULL,
    issued_at TIMESTAMP(3) NOT NULL,
    consumed_at TIMESTAMP(3),
    CONSTRAINT pk_np_soul_token PRIMARY KEY (token_id)
);

CREATE INDEX idx_np_soul_token_owner ON np_soul_token (owner);

CREATE TABLE np_pet_storage (
    pet_id CHAR(36) NOT NULL,
    contents BLOB NOT NULL,
    CONSTRAINT pk_np_pet_storage PRIMARY KEY (pet_id),
    CONSTRAINT fk_np_pet_storage_pet FOREIGN KEY (pet_id)
        REFERENCES np_pet (id) ON DELETE CASCADE
);
