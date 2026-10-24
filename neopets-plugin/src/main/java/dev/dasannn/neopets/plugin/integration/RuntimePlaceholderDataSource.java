package dev.dasannn.neopets.plugin.integration;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetVariant;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.port.RankLimitsResolver;
import dev.dasannn.neopets.plugin.config.ConfigurationManager;
import dev.dasannn.neopets.platform.paper.LivePetRegistry;
import dev.dasannn.neopets.storage.cache.PlayerDataCache;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Builds placeholder snapshots exclusively from the loaded cache and live-pet registry. */
public final class RuntimePlaceholderDataSource implements PlaceholderDataSource, Listener {
    private final Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();
    private final PlayerDataCache cache;
    private final LivePetRegistry livePets;
    private final RankLimitsResolver rankLimits;
    private final Function<OwnedPet, String> petNames;

    /** Creates the runtime snapshot adapter. */
    public RuntimePlaceholderDataSource(
            PlayerDataCache cache,
            LivePetRegistry livePets,
            RankLimitsResolver rankLimits,
            ConfigurationManager configuration,
            Collection<UUID> onlinePlayers
    ) {
        this(cache, livePets, rankLimits, petNames(configuration), onlinePlayers);
    }

    RuntimePlaceholderDataSource(
            PlayerDataCache cache,
            LivePetRegistry livePets,
            RankLimitsResolver rankLimits,
            Function<OwnedPet, String> petNames,
            Collection<UUID> onlinePlayers
    ) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.livePets = Objects.requireNonNull(livePets, "livePets");
        this.rankLimits = Objects.requireNonNull(rankLimits, "rankLimits");
        this.petNames = Objects.requireNonNull(petNames, "petNames");
        this.onlinePlayers.addAll(Objects.requireNonNull(onlinePlayers, "onlinePlayers"));
    }

    /** Publishes online status without requiring Bukkit calls during placeholder requests. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        onlinePlayers.add(event.getPlayer().getUniqueId());
    }

    /** Hides data immediately, even when asynchronous cache eviction is still pending. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        onlinePlayers.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public Optional<PlaceholderSnapshot> snapshot(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        if (!onlinePlayers.contains(playerUuid)) {
            return Optional.empty();
        }
        var playerId = new PlayerId(playerUuid);
        return cache.loadedProfile(playerId).map(profile -> {
            Set<PetId> activeIds = livePets.snapshot().keySet();
            var activePets = profile.ownedPets().stream()
                    .filter(pet -> activeIds.contains(pet.id()))
                    .toList();
            var limits = rankLimits.resolve(playerId);
            return new PlaceholderSnapshot(
                    profile.skillPointBalance(),
                    limits.maxSkillPointsPerPet(),
                    profile.ownedPets().size(),
                    activePets.size(),
                    limits.maxActivePets(),
                    activePets.stream().findFirst().map(petNames));
        });
    }

    private static Function<OwnedPet, String> petNames(ConfigurationManager configuration) {
        Objects.requireNonNull(configuration, "configuration");
        return pet -> pet.customName().orElseGet(() -> configuration.current().variants()
                .findById(pet.variant()).map(PetVariant::displayNameKey)
                .orElse(pet.variant().value()));
    }
}
