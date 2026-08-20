package io.akka.browseruse.domain;

import java.util.List;

/** What came back from one step: which step it was for, what the agent did, what it saw,
 * and anything it said about its plan — SPEC-001 §2, §3.
 *
 * <p>The step number is carried rather than assumed, because outcomes arrive over a
 * network here and a retried delivery must be recognisable (rule R19). */
public record StepOutcome(int stepNumber, List<ActionResult> results, boolean done,
    boolean success, String finalResult, List<String> planUpdate, Integer currentPlanItem,
    PageObservation page, boolean timedOut) {

  public static StepOutcome of(int stepNumber, List<ActionResult> results) {
    return new StepOutcome(stepNumber, results, false, false, null, null, null, null, false);
  }

  public static StepOutcome of(int stepNumber, List<ActionResult> results, PageObservation page) {
    return new StepOutcome(stepNumber, results, false, false, null, null, null, page, false);
  }

  public static StepOutcome plan(int stepNumber, List<String> planUpdate) {
    return new StepOutcome(stepNumber, List.of(), false, false, null, planUpdate, null, null, false);
  }

  public static StepOutcome planItem(int stepNumber, int currentPlanItem) {
    return new StepOutcome(stepNumber, List.of(), false, false, null, null, currentPlanItem, null,
        false);
  }

  public static StepOutcome done(int stepNumber, boolean success, String finalResult) {
    return new StepOutcome(stepNumber, List.of(new ActionResult("done", java.util.Map.of(), null)),
        true, success, finalResult, null, null, null, false);
  }

  public static StepOutcome timedOut(int stepNumber) {
    return new StepOutcome(stepNumber,
        List.of(new ActionResult("step", java.util.Map.of(), "step timed out")), false, false, null,
        null, null, null, true);
  }

  public List<ActionResult> resultsOrEmpty() {
    return results == null ? List.of() : results;
  }
}
