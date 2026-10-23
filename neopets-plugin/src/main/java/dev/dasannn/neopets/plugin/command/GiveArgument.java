package dev.dasannn.neopets.plugin.command;

import dev.dasannn.neopets.core.model.PetTypeId;
import dev.dasannn.neopets.core.model.VariantId;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Parsed selection accepted by {@code /neopet give}. */
public sealed interface GiveArgument permits GiveArgument.Specific, GiveArgument.Candidates, GiveArgument.Random {
    /** Parses the selection tokens following the target player. */
    static GiveArgument parse(String... tokens) {
        if (tokens.length == 0 || tokens.length > 2) {
            throw new IllegalArgumentException("expected a variant, a comma-separated list, or random [type]");
        }
        String first = tokens[0].toLowerCase(Locale.ROOT);
        if (first.equals("random")) {
            return new Random(tokens.length == 2
                    ? Optional.of(new PetTypeId(tokens[1].toLowerCase(Locale.ROOT)))
                    : Optional.empty());
        }
        if (tokens.length != 1) {
            throw new IllegalArgumentException("a pet type is only accepted after random");
        }

        String[] rawIds = first.split(",", -1);
        var ids = new LinkedHashSet<VariantId>();
        Arrays.stream(rawIds).map(String::trim).map(VariantId::new).forEach(ids::add);
        if (ids.size() == 1 && rawIds.length == 1) {
            return new Specific(ids.getFirst());
        }
        return new Candidates(List.copyOf(ids));
    }

    /** One exact variant. */
    record Specific(VariantId variantId) implements GiveArgument {
    }

    /** A weighted candidate list. */
    record Candidates(List<VariantId> variantIds) implements GiveArgument {
        public Candidates {
            variantIds = List.copyOf(variantIds);
            if (variantIds.isEmpty()) {
                throw new IllegalArgumentException("variantIds must not be empty");
            }
        }
    }

    /** All variants, optionally restricted to one type. */
    record Random(Optional<PetTypeId> typeId) implements GiveArgument {
        public Random {
            typeId = Optional.ofNullable(typeId).orElseThrow();
        }
    }
}
