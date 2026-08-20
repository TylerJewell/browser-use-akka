package io.akka.browseruse;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import io.akka.browseruse.api.AgentRunEndpoint;
import io.akka.browseruse.application.AgentRunEntity;
import io.akka.browseruse.domain.ActionResult;
import io.akka.browseruse.domain.AgentRunState;
import io.akka.browseruse.domain.RunOutcome;
import io.akka.browseruse.domain.StepOutcome;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** SPEC-001 §3, reached the way something outside a test reaches it — over HTTP, against a
 * started runtime. Everything else in this port drives the loop in-process; this is the
 * only place that answers whether the capability has a reachable surface at all. */
public class AgentRunHttpIntegrationTest extends TestKitSupport {

  private AgentRunState start(String runId, int maxSteps) {
    return httpClient.POST("/runs/" + runId)
        .withRequestBody(new AgentRunEndpoint.StartRequest("find the price", maxSteps, 5, true))
        .responseBodyAs(AgentRunState.class)
        .invoke()
        .body();
  }

  private AgentRunEntity.StepReply step(String runId, StepOutcome outcome) {
    return httpClient.POST("/runs/" + runId + "/steps")
        .withRequestBody(outcome)
        .responseBodyAs(AgentRunEntity.StepReply.class)
        .invoke()
        .body();
  }

  private AgentRunState get(String runId) {
    return httpClient.GET("/runs/" + runId).responseBodyAs(AgentRunState.class).invoke().body();
  }

  private static StepOutcome failing(int step) {
    return StepOutcome.of(step, List.of(new ActionResult("click", Map.of("index", 1), "boom")));
  }

  @Test
  void aRunIsStartedSteppedAndReadBackOverHttp() {
    var runId = "http-run-1";
    assertThat(start(runId, 50).outcome()).isEqualTo(RunOutcome.running);

    var reply = step(runId, failing(0));
    assertThat(reply.state().consecutiveFailures()).isEqualTo(1);

    step(runId, StepOutcome.plan(1, List.of("open the page", "read the price")));
    var withPlan = get(runId);
    assertThat(withPlan.plan().items()).hasSize(2);
    assertThat(withPlan.stepNumber()).isEqualTo(2);
  }

  @Test
  void aRunEndsOverHttpAfterSixConsecutiveFailures() {
    var runId = "http-run-2";
    start(runId, 50);
    for (int s = 0; s < 6; s++) {
      step(runId, failing(s));
    }
    assertThat(get(runId).outcome()).isEqualTo(RunOutcome.failedTooManyFailures);
  }

  @Test
  void aStepOutcomeDeliveredTwiceOverHttpLeavesTheRunWhereItWas() {
    var runId = "http-run-3";
    start(runId, 50);
    step(runId, failing(0));
    step(runId, failing(1));

    var retry = step(runId, failing(0));

    assertThat(retry.state().consecutiveFailures()).isEqualTo(2);
    assertThat(retry.state().stepNumber()).isEqualTo(2);
    assertThat(get(runId).consecutiveFailures()).isEqualTo(2);
  }

  @Test
  void theInstructionsForTheNextStepAreReadableOverHttp() {
    var runId = "http-run-4";
    start(runId, 4);
    var first = step(runId, failing(0));
    assertThat(first.directives().messages()).isEmpty();
    assertThat(first.directives().reportOnly()).isFalse();

    // Three of four steps spent: the warning is due, and the next step is the last one.
    var second = step(runId, failing(1));
    assertThat(second.directives().messages()).anyMatch(m -> m.startsWith("BUDGET WARNING"));
    assertThat(second.directives().reportOnly()).isFalse();

    var last = step(runId, failing(2));
    assertThat(last.state().stepNumber()).isEqualTo(3);
    assertThat(last.directives().reportOnly()).isTrue();

    var spent = step(runId, failing(3));
    assertThat(spent.state().outcome()).isEqualTo(RunOutcome.failedOutOfSteps);
  }
}
