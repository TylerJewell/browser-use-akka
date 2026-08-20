package io.akka.browseruse.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** SPEC-001 §3 rule R10: what a new plan does to the old one, and what an index does to
 * the items it passes over. */
class PlanTest {

  private static List<PlanItemStatus> statuses(Plan plan) {
    return plan.items().stream().map(PlanItem::status).toList();
  }

  @Test
  void aNewPlanReplacesTheOldOneAndRestartsAtItsFirstItem() {
    var old = Plan.of(List.of("old1", "old2"), 1).advanceTo(1);
    var fresh = Plan.of(List.of("new1", "new2", "new3"), 4);
    assertThat(fresh.items().stream().map(PlanItem::text).toList())
        .containsExactly("new1", "new2", "new3");
    assertThat(fresh.currentIndex()).isZero();
    assertThat(fresh.items().get(0).status()).isEqualTo(PlanItemStatus.current);
    assertThat(fresh.generationStep()).isEqualTo(4);
    assertThat(old.currentIndex()).isEqualTo(1);
  }

  @Test
  void advancingMarksEveryItemPassedOverAsDone() {
    var plan = Plan.of(List.of("a", "b", "c", "d"), 0).advanceTo(2);
    assertThat(statuses(plan)).containsExactly(
        PlanItemStatus.done, PlanItemStatus.done, PlanItemStatus.current, PlanItemStatus.pending);
    assertThat(plan.currentIndex()).isEqualTo(2);
  }

  @Test
  void anIndexPastTheEndIsClampedRatherThanRejected() {
    var plan = Plan.of(List.of("a", "b", "c", "d"), 0).advanceTo(99);
    assertThat(plan.currentIndex()).isEqualTo(3);
  }

  @Test
  void aNegativeIndexIsClampedToTheFirstItem() {
    var plan = Plan.of(List.of("a", "b", "c", "d"), 0).advanceTo(2).advanceTo(-5);
    assertThat(plan.currentIndex()).isZero();
  }

  @Test
  void movingBackwardsLeavesTheItemsBehindItAlone() {
    var plan = Plan.of(List.of("a", "b", "c"), 0).advanceTo(2).advanceTo(1);
    assertThat(plan.currentIndex()).isEqualTo(1);
    assertThat(statuses(plan)).containsExactly(
        PlanItemStatus.done, PlanItemStatus.current, PlanItemStatus.current);
  }

  @Test
  void anEmptyPlanHasNoCurrentItem() {
    var plan = Plan.of(List.of(), 3);
    assertThat(plan.items()).isEmpty();
    assertThat(plan.currentIndex()).isZero();
  }
}
