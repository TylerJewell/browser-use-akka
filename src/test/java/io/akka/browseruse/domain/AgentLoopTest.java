package io.akka.browseruse.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** SPEC-001 §3 rules R1–R12 and R18: the failure count and what moves it, the two ways the
 * agent gets restricted to reporting, the three ways a run ends, and the order the nudges
 * come in. */
class AgentLoopTest {

  private static AgentRunState fresh(int maxSteps) {
    return AgentRunState.started("run-1", "find the price", maxSteps, 5, true);
  }

  private static StepOutcome failing(int step) {
    return StepOutcome.of(step, List.of(new ActionResult("click", Map.of("index", 1), "boom")));
  }

  private static StepOutcome succeeding(int step) {
    return StepOutcome.of(step, List.of(new ActionResult("click", Map.of("index", 1), null)));
  }

  private static AgentRunState after(AgentRunState state, StepOutcome... outcomes) {
    var s = state;
    for (var o : outcomes) {
      s = AgentLoop.apply(s, o);
    }
    return s;
  }

  @Test
  void aSingleActionStepThatErrorsCountsAsAFailure() {
    var s = after(fresh(50), failing(0), failing(1), failing(2));
    assertThat(s.consecutiveFailures()).isEqualTo(3);
  }

  @Test
  void aMultiActionStepThatErrorsDoesNotCountAsAFailure() {
    var outcome = StepOutcome.of(0, List.of(
        new ActionResult("click", Map.of("index", 1), null),
        new ActionResult("input", Map.of("index", 2, "text", "x"), "boom")));
    assertThat(after(fresh(50), outcome).consecutiveFailures()).isZero();
  }

  @Test
  void aStepWithoutASingleActionErrorClearsTheFailureCount() {
    var s = after(fresh(50), failing(0), failing(1), succeeding(2));
    assertThat(s.consecutiveFailures()).isZero();
  }

  @Test
  void theCountIsOfConsecutiveFailuresSoASuccessInBetweenStartsItOver() {
    var s = after(fresh(50), failing(0), failing(1), failing(2), failing(3), succeeding(4), failing(5));
    assertThat(s.consecutiveFailures()).isEqualTo(1);
    assertThat(s.outcome()).isEqualTo(RunOutcome.running);
  }

  @Test
  void theRunEndsAfterFiveFailuresPlusOneReportingStep() {
    var s = fresh(50);
    for (int step = 0; step < 5; step++) {
      s = AgentLoop.apply(s, failing(step));
      assertThat(s.outcome()).isEqualTo(RunOutcome.running);
    }
    s = AgentLoop.apply(s, failing(5));
    assertThat(s.consecutiveFailures()).isEqualTo(6);
    assertThat(s.outcome()).isEqualTo(RunOutcome.failedTooManyFailures);
  }

  @Test
  void withoutTheExtraReportingStepTheRunEndsAtTheFailureLimit() {
    var s = AgentRunState.started("run-1", "t", 50, 5, false);
    for (int step = 0; step < 5; step++) {
      s = AgentLoop.apply(s, failing(step));
    }
    assertThat(s.outcome()).isEqualTo(RunOutcome.failedTooManyFailures);
  }

  @Test
  void theStepAfterTheFailureLimitIsRestrictedToReporting() {
    var s = fresh(50);
    for (int step = 0; step < 4; step++) {
      s = AgentLoop.apply(s, failing(step));
    }
    assertThat(AgentLoop.directives(s).reportOnly()).isFalse();
    s = AgentLoop.apply(s, failing(4));
    assertThat(AgentLoop.directives(s).reportOnly()).isTrue();
  }

  @Test
  void theLastStepIsTheOneBeforeTheBudgetIsSpentAndIsRestrictedToReporting() {
    var s = fresh(10);
    for (int step = 0; step < 8; step++) {
      s = AgentLoop.apply(s, succeeding(step));
    }
    assertThat(s.stepNumber()).isEqualTo(8);
    assertThat(AgentLoop.directives(s).reportOnly()).isFalse();
    s = AgentLoop.apply(s, succeeding(8));
    assertThat(s.stepNumber()).isEqualTo(9);
    assertThat(AgentLoop.directives(s).reportOnly()).isTrue();
  }

  @Test
  void theBudgetWarningStartsAtThreeQuartersSpentAndStopsOnTheLastStep() {
    var s = fresh(10);
    var warned = new java.util.ArrayList<Integer>();
    for (int step = 0; step < 10; step++) {
      if (AgentLoop.directives(s).messages().stream().anyMatch(m -> m.startsWith("BUDGET WARNING"))) {
        warned.add(s.stepNumber());
      }
      s = AgentLoop.apply(s, succeeding(step));
    }
    assertThat(warned).containsExactly(7, 8);
  }

  @Test
  void aReplanIsSuggestedFromTheThirdFailureAndOnlyWhenAPlanExists() {
    var withPlan = AgentLoop.apply(fresh(50), StepOutcome.plan(0, List.of("a", "b")));
    var s = withPlan;
    var suggested = new java.util.ArrayList<Integer>();
    for (int i = 0; i < 5; i++) {
      if (AgentLoop.directives(s).messages().stream().anyMatch(m -> m.startsWith("REPLAN SUGGESTED"))) {
        suggested.add(s.consecutiveFailures());
      }
      s = AgentLoop.apply(s, failing(s.stepNumber()));
    }
    assertThat(suggested).containsExactly(3, 4);

    var noPlan = fresh(50);
    for (int i = 0; i < 4; i++) {
      noPlan = AgentLoop.apply(noPlan, failing(noPlan.stepNumber()));
    }
    assertThat(AgentLoop.directives(noPlan).messages())
        .noneMatch(m -> m.startsWith("REPLAN SUGGESTED"));
  }

  @Test
  void aPlanIsAskedForOnceFiveStepsHaveGoneByAndNeverOnceAPlanExists() {
    var s = fresh(50);
    var nudged = new java.util.ArrayList<Integer>();
    for (int step = 0; step < 8; step++) {
      if (AgentLoop.directives(s).messages().stream().anyMatch(m -> m.startsWith("PLANNING NUDGE"))) {
        nudged.add(s.stepNumber());
      }
      s = AgentLoop.apply(s, succeeding(step));
    }
    assertThat(nudged).containsExactly(4, 5, 6, 7);

    var planned = AgentLoop.apply(s, StepOutcome.plan(s.stepNumber(), List.of("a")));
    assertThat(AgentLoop.directives(planned).messages())
        .noneMatch(m -> m.startsWith("PLANNING NUDGE"));
  }

  @Test
  void whenSeveralNudgesApplyTheyComeInBudgetReplanPlanningLoopOrder() {
    // A run near the end of a short budget, failing repeatedly on the same element, with
    // no plan: the budget, planning and loop nudges are all due on the same step.
    var s = AgentRunState.started("run-1", "t", 8, 20, true);
    for (int step = 0; step < 6; step++) {
      s = AgentLoop.apply(s, StepOutcome.of(step,
          List.of(new ActionResult("click", Map.of("index", 1), "boom")),
          new PageObservation("https://x.test", "<same>", 3)));
    }
    var messages = AgentLoop.directives(s).messages();
    assertThat(messages.stream().map(m -> m.split(":")[0]).toList())
        .containsExactly("BUDGET WARNING", "PLANNING NUDGE", "Heads up");
  }

  @Test
  void aDoneStepEndsTheRunSuccessfully() {
    var s = AgentLoop.apply(fresh(50), StepOutcome.done(0, true, "the price is 12"));
    assertThat(s.outcome()).isEqualTo(RunOutcome.succeeded);
    assertThat(s.finalResult()).isEqualTo("the price is 12");
  }

  @Test
  void aDoneStepReportingFailureStillEndsTheRunButNotSuccessfully() {
    var s = AgentLoop.apply(fresh(50), StepOutcome.done(0, false, "could not find it"));
    assertThat(s.outcome()).isEqualTo(RunOutcome.stopped);
    assertThat(s.finalResult()).isEqualTo("could not find it");
  }

  @Test
  void aRunThatSpendsItsBudgetWithoutFinishingEndsOutOfStepsRatherThanThrowing() {
    var s = fresh(3);
    for (int step = 0; step < 3; step++) {
      s = AgentLoop.apply(s, succeeding(step));
    }
    assertThat(s.stepNumber()).isEqualTo(3);
    assertThat(s.outcome()).isEqualTo(RunOutcome.failedOutOfSteps);
  }

  @Test
  void aTimedOutStepCountsAsOneFailureAndDoesNotEndTheRunByItself() {
    var s = AgentLoop.apply(fresh(50), StepOutcome.timedOut(0));
    assertThat(s.consecutiveFailures()).isEqualTo(1);
    assertThat(s.stepNumber()).isEqualTo(1);
    assertThat(s.outcome()).isEqualTo(RunOutcome.running);
  }

  @Test
  void nudgesNeverEndARunOnTheirOwn() {
    var s = AgentRunState.started("run-1", "t", 100, 20, true);
    for (int step = 0; step < 30; step++) {
      s = AgentLoop.apply(s, StepOutcome.of(step,
          List.of(new ActionResult("click", Map.of("index", 1), null)),
          new PageObservation("https://x.test", "<same>", 3)));
    }
    assertThat(AgentLoop.directives(s).messages()).anyMatch(m -> m.startsWith("Heads up"));
    assertThat(s.outcome()).isEqualTo(RunOutcome.running);
    assertThat(AgentLoop.directives(s).reportOnly()).isFalse();
  }

  @Test
  void anOutcomeForAStepAlreadyPassedIsIgnored() {
    var s = after(fresh(50), succeeding(0), succeeding(1));
    var again = AgentLoop.apply(s, failing(0));
    assertThat(again).isEqualTo(s);
  }

  @Test
  void anOutcomeForAStepAheadOfTheRunIsAlsoIgnored() {
    var s = after(fresh(50), succeeding(0));
    assertThat(AgentLoop.apply(s, failing(7))).isEqualTo(s);
  }

  @Test
  void nothingIsAppliedOnceTheRunHasEnded() {
    var ended = AgentLoop.apply(fresh(50), StepOutcome.done(0, true, "found"));
    assertThat(AgentLoop.apply(ended, failing(1))).isEqualTo(ended);
  }
}
