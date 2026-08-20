package io.akka.browseruse.domain;

/** Everything the loop decides from — SPEC-001 §2.
 *
 * <p>A null {@code plan} and an empty one are different states: only the null one asks the
 * agent to make a plan. */
public record AgentRunState(String runId, String task, int stepNumber, int maxSteps,
    int consecutiveFailures, int maxFailures, boolean finalResponseAfterFailure, Plan plan,
    LoopDetector loopDetector, RunOutcome outcome, String finalResult) {

  public static AgentRunState empty(String runId) {
    return new AgentRunState(runId, null, 0, 0, 0, 0, false, null, LoopDetector.empty(),
        RunOutcome.running, null);
  }

  public static AgentRunState started(String runId, String task, int maxSteps, int maxFailures,
      boolean finalResponseAfterFailure) {
    return new AgentRunState(runId, task, 0, maxSteps, 0, maxFailures, finalResponseAfterFailure,
        null, LoopDetector.empty(), RunOutcome.running, null);
  }

  public boolean started() {
    return task != null;
  }

  /** The count at which the run stops — the failure limit, plus the one report-only step
   * that limit buys when a final response is wanted. */
  public int failureStopThreshold() {
    return maxFailures + (finalResponseAfterFailure ? 1 : 0);
  }

  public boolean isLastStep() {
    return stepNumber >= maxSteps - 1;
  }
}
