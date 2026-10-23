package dev.dasannn.neopets.plugin.command;

import static org.junit.jupiter.api.Assertions.*;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetStatus;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.core.skilltree.ResetResult;
import dev.dasannn.neopets.core.skilltree.TreeProgress;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class AdminResetWorkflowTest {
    private final PlayerId owner = new PlayerId(UUID.randomUUID());
    private final OwnedPet pet = new OwnedPet(new PetId(UUID.randomUUID()), owner, new VariantId("dog_pale"),
            Optional.of("Luna Azul"), PetStatus.ALIVE, false, Instant.EPOCH);

    @Test
    void resolvesIdVariantAndCustomNameAndReportsReturnedPoints() {
        for (String token : List.of(pet.id().toString(), "dog_pale", "luna azul")) {
            var pending = new CompletableFuture<ResetResult>();
            var result = AdminResetWorkflow.execute(owner, List.of(pet), token, (player, id) -> {
                assertEquals(owner, player);
                assertEquals(pet.id(), id);
                return pending;
            });
            assertFalse(result.isDone());
            pending.complete(new ResetResult.Reset(new TreeProgress(pet.id(), Map.of()), 3));
            assertEquals(new MessageSpec("skills.reset-admin", Map.of("player", "Ana", "pet", "Luna Azul",
                    "points", "3")), AdminResetWorkflow.message(result.join(), "Ana", token, value -> "Luna Azul"));
        }
    }

    @Test
    void offlineUnknownAndForeignPetsDoNotInvokeReset() {
        var offline = AdminResetWorkflow.execute(null, List.of(), "dog_pale", (p, id) -> fail());
        assertEquals("errors.player-offline", AdminResetWorkflow.message(offline.join(), "Ana", "dog_pale", p -> "").key());
        var unknown = AdminResetWorkflow.execute(owner, List.of(), "unknown", (p, id) -> fail());
        assertInstanceOf(AdminResetWorkflow.Result.UnknownPet.class, unknown.join());
        var foreign = AdminResetWorkflow.execute(new PlayerId(UUID.randomUUID()), List.of(pet),
                pet.id().toString(), (p, id) -> fail());
        assertEquals("pets.not-owned", AdminResetWorkflow.message(foreign.join(), "Ana", "", p -> "").key());
    }

    @Test
    void mapsDomainOwnershipAndInfrastructureFailures() {
        for (ResetResult outcome : List.of(new ResetResult.NotOwned(pet.id()),
                new ResetResult.Failed(new IllegalStateException()))) {
            var result = AdminResetWorkflow.execute(owner, List.of(pet), "dog_pale",
                    (p, id) -> CompletableFuture.completedFuture(outcome)).join();
            assertEquals(outcome instanceof ResetResult.NotOwned ? "pets.not-owned" : "errors.internal",
                    AdminResetWorkflow.message(result, "Ana", "dog_pale", p -> "").key());
        }
    }

    @Test
    void completionUsesOnlySelectedPlayersPetsAndMatchesPrefixes() {
        assertEquals(List.of("Azul"), AdminResetWorkflow.suggestions(List.of(pet), List.of("Luna", "A")));
        assertEquals(List.of("Azul"), AdminResetWorkflow.suggestions(List.of(pet), List.of("Luna", "")));
        assertEquals(List.of("Luna Azul"), AdminResetWorkflow.suggestions(List.of(pet), List.of("lu")));
        assertTrue(AdminResetWorkflow.suggestions(List.of(pet), List.of("")).containsAll(
                List.of(pet.id().toString(), "dog_pale", "Luna Azul")));
        assertTrue(AdminResetWorkflow.suggestions(List.of(), List.of("")).isEmpty());
        assertTrue(AdminResetWorkflow.suggestions(List.of(pet), List.of("cat")).isEmpty());
    }
}
