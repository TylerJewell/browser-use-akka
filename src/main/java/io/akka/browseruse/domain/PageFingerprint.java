package io.akka.browseruse.domain;

/** What makes two page observations "the same page" for stagnation counting — SPEC-001 §3
 * rule R16. */
public record PageFingerprint(String url, int domHash, int elementCount) {

  public static PageFingerprint of(String url, String domText, int elementCount) {
    return new PageFingerprint(url == null ? "" : url,
        (domText == null ? "" : domText).hashCode(), elementCount);
  }
}
