package io.akka.browseruse.domain;

import akka.javasdk.annotations.TypeName;
import java.util.List;

/** Everything that can happen to a run — SPEC-001 §2.
 *
 * <p>Only the step's own outcome is recorded, never the state it produced: the counters,
 * the plan and the loop window are all derived by replaying {@link AgentLoop#apply} over
 * these, so a stored state and the rules can never disagree. */
public sealed interface AgentRunEvent {

  @TypeName("run-started")
  record RunStarted(String task, int maxSteps, int maxFailures, boolean finalResponseAfterFailure)
      implements AgentRunEvent {}

  @TypeName("step-applied")
  record StepApplied(StepOutcome outcome) implements AgentRunEvent {}

  static AgentRunState fold(AgentRunState state, AgentRunEvent event) {
    return switch (event) {
      case RunStarted started -> AgentRunState.started(state.runId(), started.task(),
          started.maxSteps(), started.maxFailures(), started.finalResponseAfterFailure());
      case StepApplied applied -> AgentLoop.apply(state, applied.outcome());
    };
  }

  static AgentRunState replay(String runId, List<?> events) {
    var state = AgentRunState.empty(runId);
    for (var event : events) {
      state = fold(state, (AgentRunEvent) event);
    }
    return state;
  }
}
