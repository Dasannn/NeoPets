package dev.dasannn.neopets.core.port;

/** Injectable source of uniformly distributed values in the range {@code [0, 1)}. */
@FunctionalInterface
public interface RandomSource {
    double nextDouble();
}
