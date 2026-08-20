package io.akka.browseruse.domain;

import java.util.List;

/** What the agent is told before its next step, and whether it may still act — SPEC-001 §3
 * rules R5 to R9 and R18. The messages are in the order they are to be delivered. */
public record StepDirectives(List<String> messages, boolean reportOnly) {}
