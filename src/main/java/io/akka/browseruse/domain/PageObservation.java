package io.akka.browseruse.domain;

/** What the step saw of the page, reduced to the three things stagnation counting uses —
 * SPEC-001 §3 rule R16. The page itself is out of scope; this is the whole of the browser
 * that reaches the loop. */
public record PageObservation(String url, String domText, int elementCount) {}
