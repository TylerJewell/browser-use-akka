package io.akka.browseruse.domain;

/** One item of the agent's plan — SPEC-001 §2. */
public record PlanItem(String text, PlanItemStatus status) {

  public PlanItem withStatus(PlanItemStatus newStatus) {
    return new PlanItem(text, newStatus);
  }
}
