package com.tygabeast.kakugane.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * A player's combat affinity: one score per archetype id, plus a running total of everything recorded.
 * Scores fade over time (exponential decay) so recent play matters most. The fading is computed only when
 * something is added or read, from the time stored here, so nothing runs in the background.
 */
public final class AffinityData {
    public static final Codec<AffinityData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("scores", Map.of()).forGetter(a -> a.scores),
            Codec.DOUBLE.optionalFieldOf("recorded", 0.0).forGetter(a -> a.recorded),
            Codec.LONG.optionalFieldOf("time", 0L).forGetter(a -> a.lastTime)
    ).apply(i, AffinityData::new));

    private final Map<String, Double> scores;
    /** Lifetime total of choice-weighted combat (does not fade); gates the first imprint. */
    private double recorded;
    private long lastTime;

    public AffinityData() {
        this(Map.of(), 0, 0);
    }

    private AffinityData(Map<String, Double> scores, double recorded, long lastTime) {
        this.scores = new HashMap<>(scores); // the codec hands us an unmodifiable map, so copy it
        this.recorded = recorded;
        this.lastTime = lastTime;
    }

    /** Applies the fade for the time passed since the last update. */
    public void decayTo(long now, long halfLifeTicks) {
        if (now <= lastTime || scores.isEmpty()) {
            lastTime = Math.max(lastTime, now);
            return;
        }
        double factor = Math.pow(0.5, (double) (now - lastTime) / halfLifeTicks);
        scores.replaceAll((k, v) -> v * factor);
        lastTime = now;
    }

    public void add(String archetype, double weight) {
        scores.merge(archetype, weight, Double::sum);
        recorded += weight;
    }

    public double score(String archetype) { return scores.getOrDefault(archetype, 0.0); }
    public double recorded() { return recorded; }
    public void reset() { scores.clear(); recorded = 0; }
}
