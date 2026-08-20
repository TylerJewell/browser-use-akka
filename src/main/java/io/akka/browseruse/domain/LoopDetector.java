package io.akka.browseruse.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Notices when the agent is going round in circles — SPEC-001 §3 rules R13 to R17.
 *
 * <p>It only ever produces text. Nothing here can end a run or refuse an action: an agent
 * repeating itself may be making progress each time, and only the agent can tell. */
public record LoopDetector(List<String> recentActionHashes, int consecutiveStagnantPages,
    PageFingerprint lastPage) {

  public static final int WINDOW = 20;
  public static final int STAGNATION_THRESHOLD = 5;

  public static LoopDetector empty() {
    return new LoopDetector(List.of(), 0, null);
  }

  public LoopDetector recordAction(String name, Map<String, Object> params) {
    if (ActionHash.isExempt(name)) {
      return this;
    }
    var window = new ArrayList<>(recentActionHashes);
    window.add(ActionHash.of(name, params));
    var trimmed = window.size() > WINDOW
        ? window.subList(window.size() - WINDOW, window.size())
        : window;
    return new LoopDetector(List.copyOf(trimmed), consecutiveStagnantPages, lastPage);
  }

  public LoopDetector recordPage(String url, String domText, int elementCount) {
    var fingerprint = PageFingerprint.of(url, domText, elementCount);
    int stagnant = fingerprint.equals(lastPage) ? consecutiveStagnantPages + 1 : 0;
    return new LoopDetector(recentActionHashes, stagnant, fingerprint);
  }

  public int maxRepetitionCount() {
    Map<String, Integer> counts = new HashMap<>();
    int max = 0;
    for (var hash : recentActionHashes) {
      max = Math.max(max, counts.merge(hash, 1, Integer::sum));
    }
    return max;
  }

  /** The nudge due for the current state, or null when nothing is worth mentioning. */
  public String nudge() {
    var parts = new ArrayList<String>();
    int repeats = maxRepetitionCount();
    if (repeats >= 12) {
      parts.add(repetitionMessage(repeats,
          "If you are making progress with each repetition, keep going. "
              + "If not, a different approach might get you there faster."));
    } else if (repeats >= 8) {
      parts.add(repetitionMessage(repeats,
          "Are you still making progress with each attempt? "
              + "If so, carry on. Otherwise, it might be worth trying a different approach."));
    } else if (repeats >= 5) {
      parts.add(repetitionMessage(repeats,
          "If this is intentional and making progress, carry on. "
              + "If not, it might be worth reconsidering your approach."));
    }
    if (consecutiveStagnantPages >= STAGNATION_THRESHOLD) {
      parts.add("The page content has not changed across " + consecutiveStagnantPages
          + " consecutive actions. Your actions might not be having the intended effect. "
          + "It could be worth trying a different element or approach.");
    }
    return parts.isEmpty() ? null : String.join("\n\n", parts);
  }

  private String repetitionMessage(int repeats, String advice) {
    return "Heads up: you have repeated a similar action " + repeats + " times in the last "
        + recentActionHashes.size() + " actions. " + advice;
  }
}
