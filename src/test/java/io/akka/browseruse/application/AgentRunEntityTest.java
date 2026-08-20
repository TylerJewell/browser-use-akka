package io.akka.browseruse.application;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.akka.browseruse.domain.ActionResult;
import io.akka.browseruse.domain.AgentRunEvent;
import io.akka.browseruse.domain.RunOutcome;
import io.akka.browseruse.domain.StepOutcome;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** SPEC-001 §3 rules R1–R19 applied durably: the same arithmetic, replayed from the run's
 * own event journal rather than held in one process, plus the rule the source has no need
 * of — R19, an outcome delivered twice. */
class AgentRunEntityTest {

  private static EventSourcedTestKit<io.akka.browseruse.domain.AgentRunState, AgentRunEvent, AgentRunEntity> started(int maxSteps) {
    var kit = EventSourcedTestKit.of("run-1", AgentRunEntity::new);
    kit.method(AgentRunEntity::start)
        .invoke(new AgentRunEntity.Start("find the price", maxSteps, 5, true));
    return kit;
  }

  private static StepOutcome failing(int step) {
    return StepOutcome.of(step, List.of(new ActionResult("click", Map.of("index", 1), "boom")));
  }

  @Test
  void aStepForARunThatWasNeverStartedIsRefused() {
    var kit = EventSourcedTestKit.of("run-2", AgentRunEntity::new);
    var refused = kit.method(AgentRunEntity::applyStep).invoke(failing(0));
    assertThat(refused.isError()).isTrue();
    assertThat(refused.getAllEvents()).isEmpty();
  }

  @Test
  void startingARunRecordsItAndLeavesItRunningAtStepZero() {
    var kit = started(50);
    var state = kit.method(AgentRunEntity::get).invoke().getReply();
    assertThat(state.task()).isEqualTo("find the price");
    assertThat(state.stepNumber()).isZero();
    assertThat(state.outcome()).isEqualTo(RunOutcome.running);
  }

  @Test
  void startingARunTwiceIsIgnoredRatherThanRestartingIt() {
    var kit = started(50);
    kit.method(AgentRunEntity::applyStep).invoke(failing(0));
    kit.method(AgentRunEntity::start).invoke(new AgentRunEntity.Start("something else", 9, 1, false));
    var state = kit.method(AgentRunEntity::get).invoke().getReply();
    assertThat(state.task()).isEqualTo("find the price");
    assertThat(state.stepNumber()).isEqualTo(1);
  }

  @Test
  void eachStepIsPersistedAndTheReplyCarriesTheNextStepsInstructions() {
    var kit = started(4);
    var first = kit.method(AgentRunEntity::applyStep).invoke(failing(0));
    assertThat(first.getAllEvents()).hasSize(1);
    assertThat(first.getReply().state().consecutiveFailures()).isEqualTo(1);

    // Three of four steps spent: the reply carries the budget warning due for the next one.
    var second = kit.method(AgentRunEntity::applyStep).invoke(failing(1));
    assertThat(second.getReply().state().stepNumber()).isEqualTo(2);
    assertThat(second.getReply().directives().messages())
        .anyMatch(m -> m.startsWith("BUDGET WARNING"));
  }

  @Test
  void theFailureCountIsDerivedFromTheJournalRatherThanHeldBesideIt() {
    var kit = started(50);
    for (int step = 0; step < 3; step++) {
      kit.method(AgentRunEntity::applyStep).invoke(failing(step));
    }
    var fromJournal = AgentRunEvent.replay("run-1", kit.getAllEvents());
    assertThat(fromJournal).isEqualTo(kit.getState());
    assertThat(fromJournal.consecutiveFailures()).isEqualTo(3);
  }

  @Test
  void theRunEndsOnTheJournalAfterSixConsecutiveFailures() {
    var kit = started(50);
    for (int step = 0; step < 6; step++) {
      kit.method(AgentRunEntity::applyStep).invoke(failing(step));
    }
    assertThat(kit.getState().outcome()).isEqualTo(RunOutcome.failedTooManyFailures);
    assertThat(kit.getAllEvents()).hasSize(7);
  }

  @Test
  void anOutcomeForAStepAlreadyPassedPersistsNothingAndChangesNothing() {
    var kit = started(50);
    kit.method(AgentRunEntity::applyStep).invoke(failing(0));
    kit.method(AgentRunEntity::applyStep).invoke(failing(1));
    var before = kit.getState();

    var retry = kit.method(AgentRunEntity::applyStep).invoke(failing(0));

    assertThat(retry.getAllEvents()).isEmpty();
    assertThat(retry.getReply().state()).isEqualTo(before);
    assertThat(kit.getState().consecutiveFailures()).isEqualTo(2);
  }

  @Test
  void aStepAfterTheRunHasEndedPersistsNothing() {
    var kit = started(50);
    kit.method(AgentRunEntity::applyStep).invoke(StepOutcome.done(0, true, "found it"));
    var after = kit.method(AgentRunEntity::applyStep).invoke(failing(1));
    assertThat(after.getAllEvents()).isEmpty();
    assertThat(kit.getState().outcome()).isEqualTo(RunOutcome.succeeded);
    assertThat(kit.getState().finalResult()).isEqualTo("found it");
  }
}
