CREATE TABLE np_pet_saddlebag (
    pet_id CHAR(36) NOT NULL,
    owner CHAR(36) NOT NULL,
    contents LONGBLOB NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_np_pet_saddlebag PRIMARY KEY (pet_id),
    CONSTRAINT fk_np_pet_saddlebag_pet FOREIGN KEY (pet_id)
        REFERENCES np_pet (id) ON DELETE CASCADE
);

CREATE INDEX idx_np_pet_saddlebag_owner ON np_pet_saddlebag (owner);
