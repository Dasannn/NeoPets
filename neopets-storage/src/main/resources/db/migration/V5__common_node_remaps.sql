-- Applied lazily against each pet's configured type: customized trees retaining
-- the old attribute must keep their existing progress unchanged.
CREATE TABLE np_tree_attribute_remap (
    pet_type VARCHAR(128) NOT NULL,
    old_attribute_id VARCHAR(128) NOT NULL,
    new_attribute_id VARCHAR(128) NOT NULL,
    CONSTRAINT pk_np_tree_attribute_remap PRIMARY KEY (pet_type, old_attribute_id)
);

INSERT INTO np_tree_attribute_remap (pet_type, old_attribute_id, new_attribute_id) VALUES
    ('dog', 'fire_resistance', 'tough_hide'),
    ('cat', 'fire_resistance', 'padded_paws'),
    ('polar_bear', 'fire_resistance', 'polar_swimmer'),
    ('donkey', 'fire_resistance', 'sure_footing');
