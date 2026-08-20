package io.akka.browseruse.domain;

import java.util.ArrayList;
import java.util.List;

/** The whole decision procedure — SPEC-001 §3.
 *
 * <p>Two functions and no state of their own: {@link #directives} says what the agent is
 * told before a step and whether it may still act, and {@link #apply} folds one step's
 * outcome into the run. Everything the loop knows is in {@link AgentRunState}, which is why
 * the rules can be checked without a browser, a language model or a runtime. */
public final class AgentLoop {

  public static final int REPLAN_ON_STALL = 3;
  public static final int EXPLORATION_LIMIT = 5;
  public static final double BUDGET_WARNING_RATIO = 0.75;

  private AgentLoop() {}

  public static StepDirectives directives(AgentRunState state) {
    var messages = new ArrayList<String>();

    int stepsUsed = state.stepNumber() + 1;
    if (state.maxSteps() > 0 && !state.isLastStep()
        && (double) stepsUsed / state.maxSteps() >= BUDGET_WARNING_RATIO) {
      int remaining = state.maxSteps() - stepsUsed;
      int percent = (int) (100.0 * stepsUsed / state.maxSteps());
      messages.add("BUDGET WARNING: You have used " + stepsUsed + "/" + state.maxSteps()
          + " steps (" + percent + "%). " + remaining + " steps remaining. "
          + "If the task cannot be completed in the remaining steps, prioritize: "
          + "(1) consolidate your results, (2) call done with what you have. "
          + "Partial results are far more valuable than exhausting all steps with nothing saved.");
    }

    if (state.plan() != null && state.consecutiveFailures() >= REPLAN_ON_STALL) {
      messages.add("REPLAN SUGGESTED: You have failed " + state.consecutiveFailures()
          + " consecutive times. Your current plan may need revision. "
          + "Output a new plan with revised steps to recover.");
    }

    if (state.plan() == null && stepsUsed >= EXPLORATION_LIMIT) {
      messages.add("PLANNING NUDGE: You have taken " + stepsUsed
          + " steps without creating a plan. If the task is complex, output a plan with clear "
          + "todo items now. If the task is already done or nearly done, call done instead.");
    }

    var loopNudge = state.loopDetector().nudge();
    if (loopNudge != null) {
      messages.add(loopNudge);
    }

    boolean reportOnly = state.isLastStep()
        || (state.finalResponseAfterFailure() && state.consecutiveFailures() >= state.maxFailures());
    return new StepDirectives(List.copyOf(messages), reportOnly);
  }

  public static AgentRunState apply(AgentRunState state, StepOutcome outcome) {
    if (state.outcome().finished() || outcome.stepNumber() != state.stepNumber()) {
      return state;
    }

    var results = outcome.resultsOrEmpty();
    int failures = results.size() == 1 && results.get(0).failed()
        ? state.consecutiveFailures() + 1
        : 0;

    var plan = state.plan();
    if (outcome.planUpdate() != null) {
      plan = Plan.of(outcome.planUpdate(), state.stepNumber());
    } else if (outcome.currentPlanItem() != null && plan != null) {
      plan = plan.advanceTo(outcome.currentPlanItem());
    }

    var detector = state.loopDetector();
    for (var result : results) {
      detector = detector.recordAction(result.name(), result.params());
    }
    if (outcome.page() != null) {
      detector = detector.recordPage(outcome.page().url(), outcome.page().domText(),
          outcome.page().elementCount());
    }

    int nextStep = state.stepNumber() + 1;
    var next = new AgentRunState(state.runId(), state.task(), nextStep, state.maxSteps(), failures,
        state.maxFailures(), state.finalResponseAfterFailure(), plan, detector,
        RunOutcome.running, state.finalResult());

    if (outcome.done()) {
      return new AgentRunState(next.runId(), next.task(), nextStep, next.maxSteps(), failures,
          next.maxFailures(), next.finalResponseAfterFailure(), plan, detector,
          outcome.success() ? RunOutcome.succeeded : RunOutcome.stopped, outcome.finalResult());
    }
    if (failures >= next.failureStopThreshold()) {
      return withOutcome(next, RunOutcome.failedTooManyFailures);
    }
    if (nextStep >= next.maxSteps()) {
      return withOutcome(next, RunOutcome.failedOutOfSteps);
    }
    return next;
  }

  private static AgentRunState withOutcome(AgentRunState state, RunOutcome outcome) {
    return new AgentRunState(state.runId(), state.task(), state.stepNumber(), state.maxSteps(),
        state.consecutiveFailures(), state.maxFailures(), state.finalResponseAfterFailure(),
        state.plan(), state.loopDetector(), outcome, state.finalResult());
  }
}
