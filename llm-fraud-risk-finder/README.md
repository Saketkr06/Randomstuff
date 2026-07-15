# LLM Fraud Risk Finder — with full real-time model validation

An LLM-based real-time transaction fraud risk scorer, built and then **validated
the way a bank model-governance team would validate it**: independent harness,
golden dataset, adversarial red team, failover drill, challenger benchmark, and
a filled validation report.

Three deliverables:

| Artifact | What it is |
|---|---|
| [`VALIDATION_CHECKLIST.md`](VALIDATION_CHECKLIST.md) | Reusable LLM / agentic AI model validation checklist template (SR 11-7 / SR 26-2 lineage, extended for GenAI + agents) |
| [`fraud_finder/`](fraud_finder/) | The model: real-time LLM scoring pipeline with guardrails and deterministic fallback |
| [`validation/`](validation/) + [`reports/VALIDATION_REPORT.md`](reports/VALIDATION_REPORT.md) | Independent validation harness and the generated, filled validation report |

## Quick start

```bash
# Run the full validation (no API key, no network — fully reproducible)
python3 validation/run_validation.py

# Score one demo transaction
python3 demo.py
```

No third-party dependencies for the offline path. `pip install anthropic` and set
`ANTHROPIC_API_KEY` to score with a live LLM instead.

## The model

Per transaction, the hot path is:

```
Transaction ──> input guardrail ──> prompt ──> LLM (timeout-budgeted) ──> output guardrail ──> RiskAssessment
                     │                                   │ timeout/error/bad schema
                     │ malformed: refuse (HOLD)          ▼
                     └────────────────────────> deterministic rules fallback (degraded=true)
```

- **Input guardrails** (`guardrails.py`): schema validation, prompt-injection
  neutralization on attacker-controllable free text (merchant names, UPI memos),
  PII redaction before anything reaches the prompt.
- **Prompt as model code** (`prompts.py`): versioned; edits trigger revalidation.
- **Output contract** (`schemas.py`): the LLM's JSON is schema-validated; anything
  non-conforming is discarded and the rules fallback answers instead — an
  unvalidated LLM output can never reach downstream systems.
- **Deterministic fallback** (`model.py`): a rules scorer that answers within
  budget when the LLM path fails, flagged `degraded=true`. It doubles as the
  challenger baseline in validation.
- **Audit log**: every request records prompt/model versions, guardrail actions,
  path taken (llm / fallback / input_rejected), score, and latency — the
  per-transaction reconstructability that agentic-AI governance expects.
- **Pluggable clients** (`llm_client.py`): `AnthropicClient` for production;
  `OfflineReferenceClient`, a deterministic LLM emulator (hash-seeded noise,
  intentionally imperfect) so validation runs are exactly reproducible.

## The validation

`validation/run_validation.py` executes the checklist's test program against a
600-transaction golden set covering account takeover, card testing, stolen card,
structuring, mule transfers — each with "subtle" low-signal variants — plus a
hard-legit false-positive stress segment (big travel spend, new devices, foreign
transactions by legitimate customers):

| Checklist § | Test | Gate |
|---|---|---|
| 3.1 Discrimination | AUC / Gini / KS, confusion matrix, per-typology recall | AUC ≥ 0.85, recall ≥ 80% |
| 3.3 Stability | repeat-run flip rate, irrelevant-perturbation sensitivity | 0% / ≤ 10% |
| 2.3 Real-time | latency p50/p95/p99, throughput, forced-timeout failover drill | p99 ≤ 300 ms, 100% fallback |
| 3.5 Red team | data-borne prompt injection, PII leakage, malicious/garbage LLM output | 100% blocked |
| 3.4 Challenger | rules baseline on the same golden set | positive Gini lift |
| 4 Drift | PSI vs a shifted production-like sample | alert fires at 0.25 |

The harness writes [`reports/VALIDATION_REPORT.md`](reports/VALIDATION_REPORT.md)
— a filled report with pass/fail per section, findings, approval conditions, and
honest limitations (synthetic labels, regex-based injection lexicon, thin
challenger margin). Exit code is non-zero if any gated section fails, so the
whole validation runs as a CI gate on any prompt/model/guardrail change.

## Why an offline reference client?

Model validation must be reproducible: the same inputs must yield the same
report. A live LLM API is neither free nor deterministic, and a vendor-side
model update would silently change your numbers — which is precisely the
"version-bump regression" risk the checklist (§4) exists to catch. The offline
client freezes the model under test so the *harness* can be developed and
demonstrated end-to-end; swapping in the pinned production LLM and re-running
is the documented go-live condition in the report.
