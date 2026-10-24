package dev.dasannn.neopets.plugin.update;

import java.math.BigInteger;
import java.util.List;
import java.util.regex.Pattern;

/** Semantic version precedence, including SNAPSHOT as a prerelease and ignoring build metadata. */
public record SemanticVersion(BigInteger major, BigInteger minor, BigInteger patch, List<String> prerelease)
        implements Comparable<SemanticVersion> {
    private static final Pattern FORMAT = Pattern.compile(
            "^v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$");
    public SemanticVersion { prerelease = List.copyOf(prerelease); }

    /** Parses a plugin version or release tag. */
    public static SemanticVersion parse(String text) {
        var match = FORMAT.matcher(text);
        if (!match.matches()) throw new IllegalArgumentException("Invalid semantic version");
        var pre = match.group(4) == null ? List.<String>of() : List.of(match.group(4).split("\\."));
        for (String part : pre) {
            if (part.matches("[0-9]+") && part.length() > 1 && part.startsWith("0")) {
                throw new IllegalArgumentException("Invalid numeric prerelease");
            }
        }
        return new SemanticVersion(new BigInteger(match.group(1)), new BigInteger(match.group(2)),
                new BigInteger(match.group(3)), pre);
    }

    @Override public int compareTo(SemanticVersion other) {
        int order = major.compareTo(other.major);
        if (order == 0) order = minor.compareTo(other.minor);
        if (order == 0) order = patch.compareTo(other.patch);
        if (order != 0) return order;
        if (prerelease.isEmpty() || other.prerelease.isEmpty()) {
            return Boolean.compare(prerelease.isEmpty(), other.prerelease.isEmpty());
        }
        for (int i = 0; i < Math.min(prerelease.size(), other.prerelease.size()); i++) {
            String a = prerelease.get(i), b = other.prerelease.get(i);
            boolean an = a.matches("[0-9]+"), bn = b.matches("[0-9]+");
            order = an && bn ? new BigInteger(a).compareTo(new BigInteger(b))
                    : an != bn ? (an ? -1 : 1) : a.compareTo(b);
            if (order != 0) return order;
        }
        return Integer.compare(prerelease.size(), other.prerelease.size());
    }
}
