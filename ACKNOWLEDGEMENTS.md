# Acknowledgements

This project is a port of **[browser-use/browser-use](https://github.com/browser-use/browser-use)**.

**Licence and copyright.** browser-use is under the MIT License, © 2024 Gregor Zunic, read
from the `LICENSE` file at the root of the clone rather than from a badge.

**What was copied verbatim.** No source file, prompt, fixture or schema was copied. Three
kinds of text were reproduced deliberately and are the only literal overlap:

- The wording of the four nudges and the two done-only restrictions, in
  `browser-use-akka/src/main/java/io/akka/browseruse/domain/AgentLoop.java`. These are the
  messages the agent is given, so a rebuild that reworded them would decide the same thing
  and say something different. They are close paraphrases rather than exact copies: the
  budget warning drops the source's parenthetical about the file system, which this port
  does not have.
- The action names the loop treats specially — `wait`, `done`, `go_back`, `click`, `input`,
  `navigate`, `scroll`, `search` — which are the source's own tool names and cannot be
  renamed without changing what the port is compatible with.
- The threshold numbers: 5 failures, one final response, 3 for a replan, 5 for a planning
  nudge, 5/8/12 for repetition, 5 for stagnation, a 20-action window, 75% of the step
  budget.

**What licence that forces.** Nothing beyond MIT attribution. The reproduced text is short
and functional, the rest is an independent implementation in a different language, and the
port carries this acknowledgement.

**Behaviour derived without copied text.** Yes, and that is the whole point of the port. The
decision procedure in `AgentLoop`, `LoopDetector`, `ActionHash` and `Plan` was derived from
running browser-use 0.13.8's own code and recording what it did — see
`docs/question-log.md` and `probes/probe_01.py`. Every rule in `specs/SPEC-001-browser-use.md`
describes browser-use's behaviour, deliberately, including the parts that look surprising.

## Also used

- [Akka SDK](https://akka.io) for the durable run and its HTTP surface.
- The clone used for every measurement was browser-use 0.13.8, installed into a virtual
  environment with `uv pip install -e .`.
