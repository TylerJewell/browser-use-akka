package io.akka.browseruse.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** SPEC-001 §3 rules R13–R17: what counts as the same action, when repetition and
 * stagnation are worth mentioning, and that neither ever stops a run. */
class LoopDetectorTest {

  private static LoopDetector repeat(String name, Map<String, Object> params, int times) {
    var d = LoopDetector.empty();
    for (int i = 0; i < times; i++) {
      d = d.recordAction(name, params);
    }
    return d;
  }

  @Test
  void clicksAreTheSameActionWhenTheElementIndexIsTheSame() {
    assertThat(repeat("click", Map.of("index", 7), 4).maxRepetitionCount()).isEqualTo(4);
  }

  @Test
  void clicksOnDifferentElementsAreDifferentActions() {
    var d = LoopDetector.empty();
    for (int i = 0; i < 6; i++) {
      d = d.recordAction("click", Map.of("index", i));
    }
    assertThat(d.maxRepetitionCount()).isEqualTo(1);
    assertThat(d.nudge()).isNull();
  }

  @Test
  void aClickIsTheSameActionWhateverElseTravelsWithIt() {
    var d = LoopDetector.empty()
        .recordAction("click", Map.of("index", 7))
        .recordAction("click", Map.of("index", 7, "whileHoldingCtrl", true));
    assertThat(d.maxRepetitionCount()).isEqualTo(2);
  }

  @Test
  void anInputIsHashedByIndexAndItsTextIgnoringCaseAndSurroundingSpace() {
    var d = LoopDetector.empty()
        .recordAction("input", Map.of("index", 2, "text", "Hello "))
        .recordAction("input", Map.of("index", 2, "text", "  hello"));
    assertThat(d.maxRepetitionCount()).isEqualTo(2);
  }

  @Test
  void aNavigationIsHashedByItsFullUrlSoDifferentPathsAreExploration() {
    var d = LoopDetector.empty()
        .recordAction("navigate", Map.of("url", "https://x.test/a"))
        .recordAction("navigate", Map.of("url", "https://x.test/b"));
    assertThat(d.maxRepetitionCount()).isEqualTo(1);
  }

  @Test
  void aSearchIsHashedByItsSortedQueryTokens() {
    var d = LoopDetector.empty()
        .recordAction("search", Map.of("query", "red green blue"))
        .recordAction("search", Map.of("query", "Blue, GREEN  red"));
    assertThat(d.maxRepetitionCount()).isEqualTo(2);
  }

  @Test
  void waitingGoingBackAndFinishingAreNeverRecorded() {
    var d = LoopDetector.empty();
    for (int i = 0; i < 8; i++) {
      d = d.recordAction("wait", Map.of("seconds", 1))
          .recordAction("go_back", Map.of())
          .recordAction("done", Map.of("success", true));
    }
    assertThat(d.maxRepetitionCount()).isZero();
    assertThat(d.nudge()).isNull();
  }

  @Test
  void repetitionIsWorthMentioningFromTheFifthTime() {
    assertThat(repeat("click", Map.of("index", 1), 4).nudge()).isNull();
    assertThat(repeat("click", Map.of("index", 1), 5).nudge()).contains("repeated a similar action 5 times");
  }

  @Test
  void theWordingEscalatesAtEightAndAtTwelve() {
    var five = repeat("click", Map.of("index", 1), 5).nudge();
    var eight = repeat("click", Map.of("index", 1), 8).nudge();
    var twelve = repeat("click", Map.of("index", 1), 12).nudge();
    assertThat(five).isNotEqualTo(eight);
    assertThat(eight).isNotEqualTo(twelve);
    assertThat(twelve).contains("12 times");
  }

  @Test
  void onlyTheLastTwentyActionsCount() {
    var d = repeat("click", Map.of("index", 1), 6);
    for (int i = 0; i < 20; i++) {
      d = d.recordAction("click", Map.of("index", 100 + i));
    }
    assertThat(d.recentActionHashes()).hasSize(20);
    assertThat(d.maxRepetitionCount()).isEqualTo(1);
  }

  @Test
  void stagnationIsWorthMentioningOnTheSixthIdenticalPage() {
    var d = LoopDetector.empty();
    Integer firstNudge = null;
    for (int i = 1; i <= 8; i++) {
      d = d.recordPage("https://x.test", "<same>", 12);
      if (firstNudge == null && d.nudge() != null) {
        firstNudge = i;
      }
    }
    assertThat(firstNudge).isEqualTo(6);
  }

  @Test
  void aChangedPageResetsStagnation() {
    var d = LoopDetector.empty();
    for (int i = 0; i < 5; i++) {
      d = d.recordPage("https://x.test", "<same>", 12);
    }
    assertThat(d.consecutiveStagnantPages()).isEqualTo(4);
    d = d.recordPage("https://x.test/next", "<other>", 30);
    assertThat(d.consecutiveStagnantPages()).isZero();
  }

  @Test
  void anUnknownActionIsHashedByNameAndItsNonNullParameters() {
    var withNull = new java.util.HashMap<String, Object>();
    withNull.put("a", 1);
    withNull.put("b", null);
    var d = LoopDetector.empty()
        .recordAction("upload", withNull)
        .recordAction("upload", Map.of("a", 1));
    assertThat(d.maxRepetitionCount()).isEqualTo(2);
  }
}
