# LLM / Agentic AI Model Validation Checklist

A reusable validation template for LLM-based and agentic AI models, structured as an
extension of classical model risk management (SR 11-7 / SR 26-2 lineage: conceptual
soundness, ongoing monitoring, outcomes analysis) with the additional control layers
that generative and agentic systems require.

**How to use:** copy this file into the model's validation workspace, fill in every
section, and attach evidence (eval outputs, red-team logs, latency traces) for each
checked item. Items marked **[Agentic]** apply only when the model takes actions
(tool calls, transactions, communications) rather than only producing text/scores.

---

## 0. Model Identification & Scoping

| Field | Value |
|---|---|
| Model name / ID | |
| Model owner / developer | |
| Validator (independent) | |
| Business use case | |
| Base model + version (pinned) | |
| Deployment mode (batch / real-time / agentic) | |
| Materiality tier | |
| Validation date / next review | |

- [ ] **Model determination**: documented decision on whether this system is a "model"
      under the bank's MRM policy (decision relevance, materiality, regulatory interaction).
- [ ] **Materiality classification**: tier assigned with rationale (customer impact,
      financial exposure, regulatory reporting linkage).
- [ ] **Intended-use statement**: what the model may and may not be used for, in writing.
- [ ] **Out-of-scope uses enumerated** and technically restricted where feasible.

## 1. Conceptual Soundness

### 1.1 Task & design fit
- [ ] Rationale documented for using a generative/LLM approach vs. a conventional
      classifier or rules engine (and hybrid design if applicable).
- [ ] Base model due diligence: benchmark evidence on tasks *similar to this use case*,
      not generic leaderboard scores; vendor documentation reviewed.
- [ ] Known failure modes of the base model documented (hallucination profile,
      instruction-following limits, language/domain gaps).

### 1.2 Prompt & configuration as model code
- [ ] System prompt, few-shot examples, temperature/top-p, max tokens, and guardrail
      configuration are **versioned artifacts** under change control.
- [ ] Prompt design rationale documented; each instruction traceable to a requirement.
- [ ] Output contract defined as a machine-validatable schema (JSON schema or equivalent).

### 1.3 Data & grounding (RAG / context)
- [ ] Retrieval corpus documented: sources, refresh cadence, ownership, permissible content.
- [ ] Chunking and embedding choices justified; retrieval precision/recall measured on a
      labeled query set.
- [ ] Context-window truncation behavior tested (what gets dropped under load?).

### 1.4 Developmental evidence substitute (vendor models)
- [ ] Where model weights are inaccessible, output-level validation against a labeled
      **golden dataset** is documented as the substitute for developmental evidence.
- [ ] Golden dataset: size, labeling methodology, class balance, coverage of known fraud
      typologies / edge cases, and refresh policy documented.

## 2. Implementation & Controls

### 2.1 Input guardrails
- [ ] Input schema validation rejects malformed records before they reach the LLM.
- [ ] **Prompt-injection detection** on all free-text fields sourced from customers or
      third parties (merchant names, memos, messages); adversarial suite passed.
- [ ] PII minimization/redaction applied to prompts where PII is not required for the task.

### 2.2 Output guardrails
- [ ] Output schema validation: non-conforming LLM output is never consumed downstream.
- [ ] Score/verdict bounds enforced (e.g., risk score clipped to [0,1]; enum tiers only).
- [ ] Refusal/abstention path defined: what the system does when the LLM declines or
      produces unusable output.

### 2.3 Resilience & fallback (real-time deployments)
- [ ] Latency budget defined (p50/p95/p99 targets) and enforced via timeout.
- [ ] **Deterministic fallback** (rules/score) engages on LLM timeout, error, or
      schema failure; fallback outputs are flagged as degraded and monitored.
- [ ] Failover drill executed and evidenced (kill the LLM dependency; system degrades
      gracefully, no dropped transactions).
- [ ] Throughput tested at expected peak TPS with headroom documented.

### 2.4 [Agentic] Action-space controls
- [ ] Complete inventory of tools/actions the agent can invoke, each with permission scope.
- [ ] High-materiality actions gated behind human approval or hard limits.
- [ ] Kill switch tested: agent can be halted mid-trajectory without inconsistent state.
- [ ] Tool-call correctness evaluated (right tool, right parameters) on a labeled suite.
- [ ] Plan quality / plan adherence metrics measured where the agent plans multi-step work.
- [ ] Trajectory-level success rate measured (per-step accuracy compounds; validate
      end-to-end, not per step).

### 2.5 Observability & audit trail
- [ ] Every request logs: input hash, prompt version, model version, retrieved context,
      raw output, parsed verdict, guardrail actions, latency, fallback flag.
- [ ] [Agentic] Full decision-trace (every prompt, retrieval, tool invocation) is
      reconstructable per transaction (OpenTelemetry or equivalent).
- [ ] Logs retained per record-retention policy and accessible to validation/audit.

## 3. Performance Assessment (Outcomes Analysis Substitute)

### 3.1 Discrimination & accuracy (scored/classification outputs)
- [ ] AUC-ROC / Gini / KS on golden set, with confidence intervals.
- [ ] Precision, recall, F1 at the operating threshold; confusion matrix.
- [ ] Threshold choice justified against business cost of false positives vs. misses.
- [ ] Performance sliced by segment (channel, amount band, customer tenure, geography)
      — no material segment-level degradation or disparate performance.

### 3.2 Generative-quality metrics (free-text outputs)
- [ ] Groundedness/faithfulness rate vs. retrieved context (target defined, e.g., >95%).
- [ ] LLM-as-judge scoring calibrated against human raters (agreement rate documented);
      the judge itself is versioned and validated.
- [ ] Error taxonomy maintained (hallucination / omission / formatting / tone) with
      severity tiers and observed rates.

### 3.3 Stability & reproducibility
- [ ] Repeat-run stability: verdict flip rate and score variance across repeated
      identical inputs measured and within tolerance.
- [ ] Sensitivity to irrelevant perturbations tested (paraphrase, field order,
      casing) — verdicts should not flip on semantically identical inputs.

### 3.4 Benchmarking / challenger
- [ ] Challenger model (rules baseline, conventional ML, or alternate prompt/model)
      evaluated on the same golden set; champion's advantage quantified.
- [ ] Cost-per-decision and latency compared alongside accuracy.

### 3.5 Adversarial robustness (red team)
- [ ] Prompt-injection suite: direct, indirect (data-borne), and multilingual attacks.
- [ ] Evasion suite: adversarial rephrasing of fraudulent patterns to dodge detection.
- [ ] Jailbreak suite against system-prompt exfiltration and instruction override.
- [ ] All failures triaged; blocking issues remediated before approval.

## 4. Ongoing Monitoring Plan

- [ ] **Version-bump regression**: full golden-set eval triggered automatically on any
      base-model, prompt, or guardrail version change (vendor upgrades are silent
      redevelopments — treat them as such).
- [ ] Input drift: PSI/CSI (or embedding-distribution distance) between validation
      population and production traffic, with alert thresholds.
- [ ] Output drift: score distribution, tier mix, fallback rate, refusal rate tracked
      with control limits.
- [ ] Live outcome feedback loop: confirmed-fraud labels joined back to model verdicts;
      rolling precision/recall reported at defined cadence.
- [ ] Latency SLA and error/fallback-rate dashboards with paging thresholds.
- [ ] Periodic re-red-teaming cadence defined (new attack patterns emerge continuously).
- [ ] Human-review sampling: X% of auto-decisions independently reviewed per period.

## 5. Governance & Documentation

- [ ] Model documentation complete: design, data, prompts, controls, limitations.
- [ ] **Limitations register**: known weaknesses stated plainly, with compensating controls.
- [ ] Validation report issued by an independent party with effective challenge evidenced.
- [ ] Approval conditions and use restrictions recorded; expiry/review date set.
- [ ] Change-management policy covers prompts, guardrail configs, corpus updates, and
      model version pins — not just code.
- [ ] Regulatory mapping maintained (MRM policy, EU AI Act tier if applicable, local
      supervisor expectations) and refreshed as AI-specific guidance lands.

---

## Validation Outcome

| Item | Result |
|---|---|
| Overall rating (Approved / Approved with conditions / Rejected) | |
| Conditions / restrictions | |
| Material findings | |
| Next validation due | |

*Template version 1.0 — extend, don't shrink: removing a section requires a documented
justification.*
