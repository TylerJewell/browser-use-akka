package io.akka.browseruse.domain;

import java.util.Map;

/** One action the agent took in a step, and how it went. A null error is a success —
 * SPEC-001 §3 rule R1. */
public record ActionResult(String name, Map<String, Object> params, String error) {

  public boolean failed() {
    return error != null && !error.isBlank();
  }
}
