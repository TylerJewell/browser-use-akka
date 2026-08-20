package io.akka.browseruse.domain;

import java.util.ArrayList;
import java.util.List;

/** The agent's plan and where it has got to — SPEC-001 §3 rule R10.
 *
 * <p>A plan is replaced wholesale, never edited: the only two things that happen to it are
 * a new one arriving and the index moving through this one. */
public record Plan(List<PlanItem> items, int currentIndex, int generationStep) {

  public static Plan of(List<String> texts, int generationStep) {
    var items = new ArrayList<PlanItem>();
    for (int i = 0; i < texts.size(); i++) {
      items.add(new PlanItem(texts.get(i), i == 0 ? PlanItemStatus.current : PlanItemStatus.pending));
    }
    return new Plan(List.copyOf(items), 0, generationStep);
  }

  /** Moves to {@code index}, marking everything passed over done. An index outside the plan
   * is clamped rather than rejected: the index comes from a language model, and a plan that
   * refuses to move is worse than one that stops at its own end. */
  public Plan advanceTo(int index) {
    if (items.isEmpty()) {
      return this;
    }
    int target = Math.max(0, Math.min(index, items.size() - 1));
    var updated = new ArrayList<>(items);
    for (int i = currentIndex; i < target; i++) {
      var status = updated.get(i).status();
      if (status == PlanItemStatus.current || status == PlanItemStatus.pending) {
        updated.set(i, updated.get(i).withStatus(PlanItemStatus.done));
      }
    }
    updated.set(target, updated.get(target).withStatus(PlanItemStatus.current));
    return new Plan(List.copyOf(updated), target, generationStep);
  }
}
