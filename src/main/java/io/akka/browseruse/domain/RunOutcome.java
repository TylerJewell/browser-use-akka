package io.akka.browseruse.domain;

/** How a run stands, and once it is over, how it ended — SPEC-001 §2. */
public enum RunOutcome {
  running,
  succeeded,
  failedTooManyFailures,
  failedOutOfSteps,
  stopped;

  public boolean finished() {
    return this != running;
  }
}
