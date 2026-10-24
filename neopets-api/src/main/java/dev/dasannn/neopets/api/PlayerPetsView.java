package dev.dasannn.neopets.api;

import java.util.List;

/**
 * An immutable, read-only snapshot of a player's NeoPets data.
 *
 * @param skillPoints the player's unassigned skill-point balance
 * @param pets the pets owned by the player
 */
public record PlayerPetsView(int skillPoints, List<PetView> pets) {

    /**
     * Creates a player data snapshot.
     *
     * @param skillPoints the player's unassigned skill-point balance
     * @param pets the pets owned by the player
     * @throws NullPointerException if {@code pets}, or one of its elements, is {@code null}
     */
    public PlayerPetsView {
        pets = List.copyOf(pets);
    }

    /**
     * Returns the player's unassigned skill-point balance.
     *
     * @return the skill-point balance
     */
    @Override
    public int skillPoints() {
        return skillPoints;
    }

    /**
     * Returns the pets owned by the player.
     *
     * @return an immutable pet list
     */
    @Override
    public List<PetView> pets() {
        return pets;
    }
}
