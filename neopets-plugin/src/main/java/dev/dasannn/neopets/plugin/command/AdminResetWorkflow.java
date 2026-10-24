package dev.dasannn.neopets.plugin.command;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.skilltree.ResetResult;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Resolves an online player's pet and delegates to the shared, sequenced reset service. */
public final class AdminResetWorkflow {
    private AdminResetWorkflow() { }

    /** Command outcomes, including the domain reset result and its refunded SP. */
    public sealed interface Result {
        /** The requested player is not connected. */
        record Offline() implements Result { }
        /** No pet matches the supplied identifier or name. */
        record UnknownPet() implements Result { }
        /** The persistent pet belongs to a different player. */
        record NotOwned() implements Result { }
        /** The shared service has completed its reset attempt. */
        record Completed(ResetResult reset, OwnedPet pet) implements Result { }
    }

    /** Executes a free reset; the supplied reset function must use the player's operation queue. */
    public static CompletableFuture<Result> execute(
            PlayerId onlinePlayer, List<OwnedPet> pets, String token,
            BiFunction<PlayerId, PetId, CompletableFuture<ResetResult>> reset
    ) {
        if (onlinePlayer == null) return CompletableFuture.completedFuture(new Result.Offline());
        PetId id;
        try {
            id = new PetId(UUID.fromString(token));
        } catch (IllegalArgumentException ignored) {
            id = null;
        }
        PetId requested = id;
        var pet = pets.stream().filter(value -> requested != null ? value.id().equals(requested)
                : value.variant().value().equalsIgnoreCase(token)
                        || value.customName().map(name -> name.equalsIgnoreCase(token)).orElse(false)).findFirst();
        if (pet.isEmpty()) return CompletableFuture.completedFuture(new Result.UnknownPet());
        OwnedPet selected = pet.orElseThrow();
        if (!selected.owner().equals(onlinePlayer)) return CompletableFuture.completedFuture(new Result.NotOwned());
        return reset.apply(onlinePlayer, selected.id()).thenApply(outcome -> new Result.Completed(outcome, selected));
    }

    /** Builds localized feedback for every command and domain outcome. */
    public static MessageSpec message(Result result, String player, String token, Function<OwnedPet, String> name) {
        return switch (result) {
            case Result.Offline ignored -> new MessageSpec("errors.player-offline", Map.of("player", player));
            case Result.NotOwned ignored -> MessageSpec.of("pets.not-owned");
            case Result.UnknownPet ignored -> new MessageSpec("pets.unknown-pet", Map.of("pet", token));
            case Result.Completed completed -> switch (completed.reset()) {
                case ResetResult.Reset reset -> new MessageSpec("skills.reset-admin", Map.of(
                        "player", player, "pet", name.apply(completed.pet()),
                        "points", Integer.toString(reset.refundedSkillPoints())));
                case ResetResult.NotOwned ignored -> MessageSpec.of("pets.not-owned");
                case ResetResult.Failed ignored -> MessageSpec.of("errors.internal");
            };
        };
    }

    /** Suggests persistent IDs, variant IDs and custom names belonging to the selected player. */
    public static Collection<String> suggestions(List<OwnedPet> pets, List<String> tokens) {
        if (tokens.isEmpty()) return List.of();
        String consumed = tokens.size() == 1 ? ""
                : String.join(" ", tokens.subList(0, tokens.size() - 1)) + " ";
        String input = consumed + tokens.getLast();
        return pets.stream().flatMap(pet -> java.util.stream.Stream.concat(
                java.util.stream.Stream.of(pet.id().toString(), pet.variant().value()), pet.customName().stream()))
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(input.toLowerCase(Locale.ROOT)))
                .map(value -> value.substring(consumed.length())).distinct().toList();
    }
}
