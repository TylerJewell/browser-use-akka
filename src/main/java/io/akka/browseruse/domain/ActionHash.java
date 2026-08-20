package io.akka.browseruse.domain;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** When two actions count as the same action — SPEC-001 §3 rule R14.
 *
 * <p>Each kind is normalised on what identifies it rather than on its whole parameter set:
 * an element index identifies a click, a full address identifies a navigation, and a
 * query's sorted words identify a search, so re-ordering the words is the same search. */
public final class ActionHash {

  private ActionHash() {}

  public static String of(String name, Map<String, Object> params) {
    var p = params == null ? Map.<String, Object>of() : params;
    return switch (name) {
      case "search" -> {
        var tokens = String.valueOf(p.getOrDefault("query", ""))
            .toLowerCase()
            .replaceAll("[^\\w\\s]", " ")
            .split("\\s+");
        var sorted = Arrays.stream(tokens)
            .filter(t -> !t.isEmpty())
            .distinct()
            .sorted()
            .collect(Collectors.joining("|"));
        yield "search|" + p.getOrDefault("engine", "google") + "|" + sorted;
      }
      case "input" -> "input|" + p.get("index") + "|"
          + String.valueOf(p.getOrDefault("text", "")).trim().toLowerCase();
      case "click" -> "click|" + p.get("index");
      case "navigate" -> "navigate|" + p.getOrDefault("url", "");
      case "scroll" -> "scroll|" + (Boolean.FALSE.equals(p.get("down")) ? "up" : "down") + "|"
          + p.get("index");
      default -> {
        var identifying = new TreeMap<String, Object>();
        p.forEach((k, v) -> {
          if (v != null) {
            identifying.put(k, v);
          }
        });
        yield name + "|" + identifying;
      }
    };
  }

  /** Waiting, finishing and going back are excluded from repetition counting: the first
   * hashes identically every time, the second is terminal, and the third is how the agent
   * recovers from a wrong turn. */
  public static boolean isExempt(String name) {
    return "wait".equals(name) || "done".equals(name) || "go_back".equals(name);
  }
}
