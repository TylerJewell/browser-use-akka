package io.akka.browseruse.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import io.akka.browseruse.application.AgentRunEntity;
import io.akka.browseruse.domain.AgentRunState;
import io.akka.browseruse.domain.StepOutcome;

/** The loop's own surface: start a run, hand it what a step did, read where it got to —
 * SPEC-001 §1. Whoever drives the browser and the language model is the caller; this port
 * decides only what the caller is told next and when to stop. It has no rendered surface;
 * see {@code gui/manifest.json} in the port's findings directory for how that was settled. */
@HttpEndpoint("/runs")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
public class AgentRunEndpoint {

  private final ComponentClient componentClient;

  public AgentRunEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public record StartRequest(String task, int maxSteps, int maxFailures,
      boolean finalResponseAfterFailure) {}

  @Post("/{runId}")
  public AgentRunState start(String runId, StartRequest request) {
    return componentClient.forEventSourcedEntity(runId)
        .method(AgentRunEntity::start)
        .invoke(new AgentRunEntity.Start(request.task(), request.maxSteps(), request.maxFailures(),
            request.finalResponseAfterFailure()));
  }

  @Post("/{runId}/steps")
  public AgentRunEntity.StepReply step(String runId, StepOutcome outcome) {
    return componentClient.forEventSourcedEntity(runId)
        .method(AgentRunEntity::applyStep)
        .invoke(outcome);
  }

  @Get("/{runId}")
  public AgentRunState get(String runId) {
    return componentClient.forEventSourcedEntity(runId)
        .method(AgentRunEntity::get)
        .invoke();
  }
}
