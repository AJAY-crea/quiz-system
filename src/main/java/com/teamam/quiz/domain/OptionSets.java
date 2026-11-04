package com.teamam.quiz.domain;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/** Stores a set of option numbers in one column as text, e.g. [2, 0] is saved as "0,2". */
public final class OptionSets {

    private OptionSets() {
    }

    /** "0,2" -> [0, 2]; null or empty -> []. */
    public static List<Integer> parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return Arrays.stream(stored.split(",")).map(String::trim).map(Integer::valueOf).sorted().toList();
    }

    /** [2, 0, 2] -> "0,2": sorted and without repeats, so two equal sets are always stored the same way. */
    public static String format(Collection<Integer> options) {
        return options.stream().distinct().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }
}
