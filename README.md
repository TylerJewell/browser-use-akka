# browser-use-akka

Decides what a browsing agent is told before each step, whether it may still act, and when
its run is over — and keeps a durable record of every one of those decisions.

A port of [browser-use/browser-use](https://github.com/browser-use/browser-use) onto
**Akka**, built with **Akka Specify**.

---

## Where it came from

browser-use is a Python library that lets a language model use a web browser: it takes a
task, looks at a page, picks an action, does it, and looks again. Wrapped around that is a
loop that counts how many steps have failed in a row, keeps track of the model's own plan,
notices when the agent is going round in circles, warns it when the step budget is running
out, and decides when to stop trying. This port takes only that loop. Left alone: the
browser, the page, the model and the prompt built for it, the recording of a finished run,
and the terminal program that drives the whole thing.

Nothing else in this collection of ports rebuilt a decision procedure that runs *between*
calls to a language model. The earlier agent port rebuilt how a workflow's steps are
defined and recorded; this one rebuilds what happens when a step goes wrong, six times in a
row, on a page that has not changed.

The specification this was built from lives in
[TylerJewell/akka-specify-harness](https://github.com/TylerJewell/akka-specify-harness)
under `browser-use-port/`.

---

## browser-use/browser-use → this port

📉 372 Python lines → **428 Java lines**<br>
📁 385 files → **16 files**<br>
⚡ 26.26 → **2.56** microseconds, one step decided<br>
🎯 366 answers compared → **366 of 366 agree**<br>
🧪 0 rules broken on purpose to check a test notices → **18**<br>
🙋 0 questions to a human → **0**

The line counts cover the same behaviour on both sides, named symbol by symbol rather than
judged by eye. The timing is the decision alone on both sides, with no browser and no
language model on either. How each number was measured, and the four things it does not
show, are written up next to the specification in `akka-specify-harness` under
`browser-use-port/bench/REPORT.md`.

---

## What it took to build

⏱️ **0.8 hours** from the first command to the published repository, **0.8** of them active<br>
💬 **168** exchanges with the model<br>
✍️ **155,123** tokens written by the model, **23,843,518** counting everything sent and re-sent<br>
🙋 **0** questions to a human<br>
🧪 **52** tests

```bash
python toolkit/tokens.py --port browser-use    # turns, tokens, elapsed and active time
```

The record of every question, and where the time went, is in
[`port-log/`](https://github.com/TylerJewell/akka-specify-harness/tree/main/port-log).

---

## What it does

From the specification:

- **A step that fails counts only when it did one thing.** A step that tried several things
  and had one of them fail is left to the circling check and the replan suggestion instead,
  so a busy step that half worked does not push the run towards giving up.
- **Five failures in a row buy one last step, and that step may only report.** The agent is
  given the chance to say what it found rather than being cut off mid-attempt.
- **The last step of the budget may only report.** The same restriction arrives a step
  earlier as a warning, from three quarters of the budget onwards, so there is time to
  gather results before the door closes.
- **A new plan replaces the old one and starts again at its first item.** Moving to a later
  item marks everything passed over as finished, and an item number outside the plan is
  pulled back to the nearest end rather than refused.
- **Noticing that the agent is repeating itself never stops it.** Five repeats of the same
  action, or six views of an unchanged page, produce a remark and nothing more — an agent
  repeating itself may be making progress each time, and only the agent can tell.
- **The same step outcome delivered twice changes nothing.** The second delivery is ignored
  and the run is returned as it stands.

---

## Design decisions

**Event sourcing.** Only what each step did is written down, never the counters worked out
from it, so a stored total can never disagree with the rules that produce it. Reading a run
back gives the same answer as watching it happen.

**A pure decision function.** All the deciding happens in one place that touches nothing
outside itself, so it can be checked without a browser, a language model, or anything
started up. Every rule is tested in a few milliseconds instead of a few minutes.

**The step number travels with the outcome.** Whoever drives the browser says which step
their result belongs to, so a message that arrives twice after a lost connection can be
recognised and dropped. A caller can safely try again without wondering whether the first
attempt landed.

**One run, one address.** Each run is stored under the name its caller chose, and nothing is
shared between runs. Two runs can never slow each other down or read each other's counters.

---

## Running it — the short path

You do not need Java, Maven, or the Akka CLI installed. Akka Specify installs them for you.

**1. Install Akka Specify** in Claude Code:

```
/plugin marketplace add akka/ai-marketplace
/plugin install akka@akka-ai-marketplace
```

Restart Claude Code when it asks.

**2. Give it this prompt:**

> Clone https://github.com/TylerJewell/browser-use-akka into a new directory and open it.
> Then run /akka:setup to install everything this project needs, and /akka:build to
> compile it, run the tests, and start it locally.

**3. Talk to it** on http://localhost:9022 — it has no screen; see below for the three
things it answers.

---

## Running it — the developer path

### Requirements

- Java 21 or newer
- Maven 3.9 or newer
- An Akka download token — run `akka code token` once

No key for a model provider: this port never calls one.

### Start the service

```bash
mvn compile
akka local run
```

The service starts on **port 9022**.

### The three things it answers

Start a run:

```bash
curl -X POST localhost:9022/runs/my-run -H 'Content-Type: application/json' \
  -d '{"task":"find the price","maxSteps":50,"maxFailures":5,"finalResponseAfterFailure":true}'
```

Tell it what a step did, and be told what to say next:

```bash
curl -X POST localhost:9022/runs/my-run/steps -H 'Content-Type: application/json' \
  -d '{"stepNumber":0,"results":[{"name":"click","params":{"index":3},"error":"element not found"}]}'
```

Read where a run got to:

```bash
curl localhost:9022/runs/my-run
```

---

## Configuration

Everything about a run is set when it starts, so there is nothing to configure by
environment variable.

| Field on the start request | Default if omitted | Notes |
|---|---|---|
| `task` | none | What the run is for. Recorded, never inspected. |
| `maxSteps` | none — must be given | The step budget. The last step may only report. |
| `maxFailures` | none — must be given | browser-use uses 5. |
| `finalResponseAfterFailure` | `false` when omitted | browser-use uses true, which buys one last reporting step. |

---

## Where it differs from browser-use/browser-use

Everything not listed here behaves the same way on purpose, including the parts that look
like mistakes.

- **Who drives the loop.** browser-use runs the loop itself: it calls the model, does the
  action, and decides, all inside one process. This port is told what a step did and
  answers with what to say next, because a loop that survives a restart cannot be the same
  thing as the program holding the browser open.
- **The same step outcome arriving twice.** browser-use cannot receive one twice, so it has
  no rule for it. This port ignores an outcome for a step it has already passed and returns
  the run unchanged, because a caller whose reply was lost has to be able to try again
  without risking a second count against the same failure.
- **How the report-only restriction is delivered.** browser-use narrows the set of tools the
  model may call and adds a sentence explaining why. This port answers with a flag saying
  the agent may only report, and leaves the wording to whoever builds the prompt, because
  the thing that has to be right is the decision rather than the sentence.
- **A step for a run that was never started.** browser-use has no such state — a run exists
  only once its loop is running. This port refuses the step rather than inventing a run,
  because a request naming a run nobody started is more likely a typo than a run.
- **Starting the same run twice.** browser-use starts a fresh loop each time it is asked.
  This port keeps the first run and ignores the second request, for the same reason it
  ignores a repeated step.
- **How long a step may take.** browser-use gives each step 180 seconds and counts a
  timeout as one failure. This port does not time anything: it never performs a step, so
  the caller is the one that can time it, and says so by sending an outcome marked as
  timed out, which counts as one failure exactly as it does in browser-use.
- **What happens across a dropped connection.** browser-use reconnects to the browser
  mid-step and retries. Not checked in this port, which never holds a browser connection —
  what a caller should do when its own browser drops is the caller's decision, and this
  port has nothing to say about it.
- **Everything the loop hands to the model beyond these decisions.** browser-use also
  compacts the message history, records the page as an image, judges the finished run, and
  sends events to its own cloud service. This port does none of it, and none of it changes
  what the loop decides.

---

## Licence

browser-use is under the MIT License, © 2024 Gregor Zunic. This port reimplements the
behaviour in Java without copied source, apart from the wording of the messages the agent
is given; see `ACKNOWLEDGEMENTS.md`.
