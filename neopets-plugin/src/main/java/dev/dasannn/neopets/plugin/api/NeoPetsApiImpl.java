package dev.dasannn.neopets.plugin.api;

import dev.dasannn.neopets.api.NeoPetsApi;
import dev.dasannn.neopets.api.PetView;
import dev.dasannn.neopets.api.PlayerPetsView;
import dev.dasannn.neopets.api.SkillTreeView;
import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.port.PetRepository;
import dev.dasannn.neopets.core.port.PlayerRepository;
import dev.dasannn.neopets.core.port.TreeProgressRepository;
import dev.dasannn.neopets.plugin.config.ConfigurationManager;
import dev.dasannn.neopets.platform.paper.LivePetRegistry;
import dev.dasannn.neopets.storage.cache.PlayerDataCache;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Thread-safe read-only API backed by the runtime repositories and live entity registry. */
public final class NeoPetsApiImpl implements NeoPetsApi {
    private final PetRepository pets;
    private final PlayerRepository players;
    private final TreeProgressRepository trees;
    private final PlayerDataCache cache;
    private final LivePetRegistry livePets;
    private final ConfigurationManager configuration;

    public NeoPetsApiImpl(
            PetRepository pets,
            PlayerRepository players,
            TreeProgressRepository trees,
            PlayerDataCache cache,
            LivePetRegistry livePets,
            ConfigurationManager configuration
    ) {
        this.pets = Objects.requireNonNull(pets, "pets");
        this.players = Objects.requireNonNull(players, "players");
        this.trees = Objects.requireNonNull(trees, "trees");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.livePets = Objects.requireNonNull(livePets, "livePets");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    @Override
    public Optional<PetView> findLoadedPet(UUID petId) {
        Objects.requireNonNull(petId, "petId");
        PetId id = new PetId(petId);
        return cache.loadedPet(id).map(this::view);
    }

    @Override
    public List<PetView> activePets(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return cache.loadedPets(new PlayerId(ownerId)).orElseGet(List::of).stream()
                .filter(pet -> livePets.entityId(pet.id()).isPresent())
                .map(this::view).toList();
    }

    @Override
    public CompletableFuture<Optional<PlayerPetsView>> loadPlayerPets(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        PlayerId playerId = new PlayerId(ownerId);
        return players.findById(playerId).thenCombine(pets.findByOwner(playerId), (profile, owned) -> {
            if (profile.isEmpty() && owned.isEmpty()) {
                return Optional.empty();
            }
            int skillPoints = profile.map(value -> value.skillPointBalance()).orElse(0);
            return Optional.of(new PlayerPetsView(skillPoints, owned.stream().map(this::view).toList()));
        });
    }

    @Override
    public CompletableFuture<Optional<SkillTreeView>> loadSkillTree(UUID petId) {
        Objects.requireNonNull(petId, "petId");
        PetId id = new PetId(petId);
        return pets.findById(id).thenCompose(pet -> {
            if (pet.isEmpty()) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            OwnedPet owned = pet.orElseThrow();
            var variant = configuration.current().variants().findById(owned.variant()).orElseThrow();
            var definition = configuration.current().skillTree(variant.type()).orElseThrow();
            return trees.load(id).thenApply(stored -> {
                var tree = stored.orElseGet(() ->
                        dev.dasannn.neopets.core.skilltree.TreeProgress.empty(id, definition));
                    var attributes = definition.attributes().stream().map(attribute -> {
                        var levels = tree.attributes().get(attribute.id());
                        return new SkillTreeView.Attribute(attribute.id(), levels.unlockedLevel(),
                                levels.activeLevel(), attribute.maxLevel());
                    }).toList();
                return Optional.of(new SkillTreeView(attributes));
            });
        });
    }

    private PetView view(OwnedPet pet) {
        var variant = configuration.current().variants().findById(pet.variant()).orElseThrow();
        String name = pet.customName().orElse(variant.displayNameKey());
        return new PetView(pet.id().value(), pet.owner().value(), pet.variant().value(),
                variant.type().value(), name,
                dev.dasannn.neopets.api.PetStatus.valueOf(pet.status().name()), pet.active());
    }
}
