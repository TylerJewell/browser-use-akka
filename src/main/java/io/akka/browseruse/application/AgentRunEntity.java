package io.akka.browseruse.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.akka.browseruse.domain.AgentLoop;
import io.akka.browseruse.domain.AgentRunEvent;
import io.akka.browseruse.domain.AgentRunState;
import io.akka.browseruse.domain.StepDirectives;
import io.akka.browseruse.domain.StepOutcome;

/** One agent run's own durable loop — SPEC-001 §2, §3.
 *
 * <p>The entity holds no rule of its own: it decides whether an outcome is worth recording,
 * persists it if so, and lets {@link AgentRunEvent#fold} apply {@link AgentLoop}. */
@Component(id = "agent-run")
public class AgentRunEntity extends EventSourcedEntity<AgentRunState, AgentRunEvent> {

  private final String runId;

  public AgentRunEntity(EventSourcedEntityContext context) {
    this.runId = context.entityId();
  }

  @Override
  public AgentRunState emptyState() {
    return AgentRunState.empty(runId);
  }

  public record Start(String task, int maxSteps, int maxFailures,
      boolean finalResponseAfterFailure) {}

  /** Starting a run that has already started is ignored, for the same reason a repeated
   * step outcome is: the caller cannot tell whether its first attempt landed. */
  public Effect<AgentRunState> start(Start command) {
    if (currentState().started()) {
      return effects().reply(currentState());
    }
    return effects()
        .persist(new AgentRunEvent.RunStarted(command.task(), command.maxSteps(),
            command.maxFailures(), command.finalResponseAfterFailure()))
        .thenReply(state -> state);
  }

  public record StepReply(AgentRunState state, StepDirectives directives) {}

  public Effect<StepReply> applyStep(StepOutcome outcome) {
    var current = currentState();
    if (!current.started()) {
      return effects().error("run " + runId + " has not been started");
    }
    // An outcome the loop would discard is not worth a journal entry: the run has ended, or
    // this is a delivery of a step it has already passed (rule R19).
    if (current.outcome().finished() || outcome.stepNumber() != current.stepNumber()) {
      return effects().reply(new StepReply(current, AgentLoop.directives(current)));
    }
    return effects()
        .persist(new AgentRunEvent.StepApplied(outcome))
        .thenReply(state -> new StepReply(state, AgentLoop.directives(state)));
  }

  public ReadOnlyEffect<AgentRunState> get() {
    return effects().reply(currentState());
  }

  @Override
  public AgentRunState applyEvent(AgentRunEvent event) {
    return AgentRunEvent.fold(currentState(), event);
  }
}
