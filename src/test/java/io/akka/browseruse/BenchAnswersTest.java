package io.akka.browseruse;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.akka.browseruse.domain.ActionResult;
import io.akka.browseruse.domain.AgentLoop;
import io.akka.browseruse.domain.AgentRunState;
import io.akka.browseruse.domain.PageObservation;
import io.akka.browseruse.domain.PlanItem;
import io.akka.browseruse.domain.StepOutcome;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Runs the benchmark scenarios through the port and writes {@code bench/port-answers.json}
 * beside the source's own answers, for {@code probes/probe_04_compare.py} to put side by
 * side. It is a test rather than a main class so it cannot rot unnoticed: if the loop stops
 * answering, this goes red with everything else. */
class BenchAnswersTest {

  private static final Path BENCH = Path.of("..", "browser-use-port", "bench");
  private static final int REPEATS = 200;
  private static final ObjectMapper JSON = new ObjectMapper();

  private static String kind(String message) {
    if (message.startsWith("BUDGET WARNING")) {
      return "BUDGET";
    }
    if (message.startsWith("REPLAN SUGGESTED")) {
      return "REPLAN";
    }
    if (message.startsWith("PLANNING NUDGE")) {
      return "PLANNING";
    }
    return "LOOP";
  }

  private static Map<String, Object> params(JsonNode node) {
    var out = new HashMap<String, Object>();
    if (node == null || node.isNull()) {
      return out;
    }
    node.fields().forEachRemaining(entry -> {
      var value = entry.getValue();
      if (value.isNull()) {
        return;
      }
      out.put(entry.getKey(),
          value.isInt() ? value.asInt() : value.isBoolean() ? value.asBoolean() : value.asText());
    });
    return out;
  }

  private static StepOutcome outcome(JsonNode step, int stepNumber) {
    var results = new ArrayList<ActionResult>();
    for (var result : step.withArray("results")) {
      var error = result.get("error");
      results.add(new ActionResult(result.get("name").asText(), params(result.get("params")),
          error == null || error.isNull() ? null : error.asText()));
    }
    List<String> planUpdate = null;
    if (step.hasNonNull("planUpdate")) {
      planUpdate = new ArrayList<>();
      for (var item : step.withArray("planUpdate")) {
        planUpdate.add(item.asText());
      }
    }
    Integer currentPlanItem =
        step.hasNonNull("currentPlanItem") ? step.get("currentPlanItem").asInt() : null;
    PageObservation page = null;
    if (step.hasNonNull("page")) {
      var p = step.get("page");
      page = new PageObservation(p.get("url").asText(), p.get("domText").asText(),
          p.get("elementCount").asInt());
    }
    boolean done = step.path("done").asBoolean(false);
    return new StepOutcome(stepNumber, results, done, step.path("success").asBoolean(false),
        step.path("finalResult").asText(null), planUpdate, currentPlanItem, page, false);
  }

  private static ArrayNode runScenario(JsonNode scenario) {
    var state = AgentRunState.started(scenario.get("name").asText(), "bench",
        scenario.get("maxSteps").asInt(), scenario.get("maxFailures").asInt(),
        scenario.get("finalResponseAfterFailure").asBoolean());
    var answers = JSON.createArrayNode();
    int index = 0;
    for (var step : scenario.withArray("steps")) {
      if (state.outcome().finished()) {
        break;
      }
      state = AgentLoop.apply(state, outcome(step, index));
      var directives = AgentLoop.directives(state);

      var answer = JSON.createObjectNode();
      answer.put("step", index);
      answer.put("consecutiveFailures", state.consecutiveFailures());
      if (state.plan() == null) {
        answer.putNull("planStatuses");
        answer.putNull("planIndex");
      } else {
        var statuses = answer.putArray("planStatuses");
        for (PlanItem item : state.plan().items()) {
          statuses.add(item.status().name());
        }
        answer.put("planIndex", state.plan().currentIndex());
      }
      var kinds = answer.putArray("messageKinds");
      directives.messages().forEach(m -> kinds.add(kind(m)));
      answer.put("reportOnly", directives.reportOnly());
      if (state.outcome().finished()) {
        answer.put("ended", state.outcome().name());
      } else {
        answer.putNull("ended");
      }
      answers.add(answer);
      index++;
    }
    return answers;
  }

  @Test
  void writesTheSameScenariosTheSourceWasRunThrough() throws Exception {
    var scenarios = JSON.readTree(Files.readString(BENCH.resolve("scenarios.json")));

    var answers = JSON.createObjectNode();
    int steps = 0;
    for (var scenario : scenarios) {
      var scenarioAnswers = runScenario(scenario);
      answers.set(scenario.get("name").asText(), scenarioAnswers);
      steps += scenarioAnswers.size();
    }
    assertThat(steps).isPositive();

    // Warm the just-in-time compiler before timing, or the first scenarios pay for the rest.
    for (int i = 0; i < 50; i++) {
      for (var scenario : scenarios) {
        runScenario(scenario);
      }
    }
    long start = System.nanoTime();
    for (int i = 0; i < REPEATS; i++) {
      for (var scenario : scenarios) {
        runScenario(scenario);
      }
    }
    double elapsedSeconds = (System.nanoTime() - start) / 1e9;

    ObjectNode out = JSON.createObjectNode();
    out.set("answers", answers);
    var timing = out.putObject("timing");
    timing.put("repeats", REPEATS);
    timing.put("stepsPerRepeat", steps);
    timing.put("totalSeconds", elapsedSeconds);
    timing.put("microsecondsPerStep", elapsedSeconds / (REPEATS * (double) steps) * 1_000_000);

    Files.writeString(BENCH.resolve("port-answers.json"),
        JSON.writerWithDefaultPrettyPrinter().writeValueAsString(out));
  }
}
