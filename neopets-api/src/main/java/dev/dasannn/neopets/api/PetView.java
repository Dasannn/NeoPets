package dev.dasannn.neopets.api;

import java.util.Objects;
import java.util.UUID;

/**
 * An immutable, read-only snapshot of a pet.
 *
 * @param id the unique pet instance identifier
 * @param owner the unique identifier of the player who owns the pet
 * @param variantId the configured variant identifier
 * @param typeId the configured pet type identifier
 * @param name the pet's current name
 * @param status the pet's lifecycle status
 * @param active whether the pet is currently summoned
 */
public record PetView(
        UUID id,
        UUID owner,
        String variantId,
        String typeId,
        String name,
        PetStatus status,
        boolean active
) {

    /**
     * Creates a pet snapshot.
     *
     * @param id the unique pet instance identifier
     * @param owner the unique identifier of the player who owns the pet
     * @param variantId the configured variant identifier
     * @param typeId the configured pet type identifier
     * @param name the pet's current name
     * @param status the pet's lifecycle status
     * @param active whether the pet is currently summoned
     * @throws NullPointerException if any reference argument is {@code null}
     */
    public PetView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(status, "status");
    }

    /**
     * Returns the unique pet instance identifier.
     *
     * @return the pet identifier
     */
    @Override
    public UUID id() {
        return id;
    }

    /**
     * Returns the unique identifier of the player who owns the pet.
     *
     * @return the owner identifier
     */
    @Override
    public UUID owner() {
        return owner;
    }

    /**
     * Returns the configured variant identifier.
     *
     * @return the variant identifier
     */
    @Override
    public String variantId() {
        return variantId;
    }

    /**
     * Returns the configured pet type identifier.
     *
     * @return the type identifier
     */
    @Override
    public String typeId() {
        return typeId;
    }

    /**
     * Returns the pet's current name.
     *
     * @return the pet name
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * Returns the pet's lifecycle status.
     *
     * @return the pet status
     */
    @Override
    public PetStatus status() {
        return status;
    }

    /**
     * Returns whether the pet is currently summoned.
     *
     * @return {@code true} when the pet is summoned
     */
    @Override
    public boolean active() {
        return active;
    }
}
