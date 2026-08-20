package io.akka.browseruse.domain;

/** How far one plan item has got — SPEC-001 §2. */
public enum PlanItemStatus {
  pending,
  current,
  done,
  skipped
}
