ALTER TABLE np_soul_token ADD COLUMN delivered_at TIMESTAMP(3);
ALTER TABLE np_soul_token ADD COLUMN active_pet_id CHAR(36);

UPDATE np_soul_token
SET active_pet_id = pet_id
WHERE consumed_at IS NULL;

CREATE UNIQUE INDEX uq_np_soul_token_active_pet
    ON np_soul_token (active_pet_id);

CREATE TABLE np_pet_death_work (
    pet_id CHAR(36) NOT NULL,
    owner CHAR(36) NOT NULL,
    keep_skills BOOLEAN NOT NULL,
    skills_handled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_np_pet_death_work PRIMARY KEY (pet_id)
);

CREATE INDEX idx_np_pet_death_work_owner
    ON np_pet_death_work (owner);
