# Validation Report — LLM Fraud Risk Finder (Real-Time)

| Field | Value |
|---|---|
| Model version | `fraud-risk-finder/1.0.0|fraud-finder-prompt/1.0.0|offline-reference/1.0.0` |
| Deployment mode | Real-time transaction scoring (budget 300 ms) |
| Golden set | 600 labeled synthetic txns, 6 fraud typologies + hard-legit FP segment |
| Operating threshold | score >= 0.32 flags fraud |
| Validation date | 2026-07-15 |
| Overall outcome | **Approved with conditions** |

> Scope note: this run validates the pipeline with the deterministic offline
> reference client, which makes every number below exactly reproducible. Before
> production use with a live LLM API, re-run this harness against the pinned
> vendor model (same golden set) and re-issue the report — a vendor/model swap
> is a model change (checklist §4, version-bump regression).

## §3.1 Discrimination — PASS

| Metric | Value | Threshold |
|---|---|---|
| AUC-ROC | 0.948 | >= 0.85 |
| Gini | 0.896 | — |
| KS | 0.813 | — |
| Precision @ 0.32 | 85.9% | — |
| Recall @ 0.32 | 81.3% | >= 80% |
| Hard-legit false-positive rate | 22.2% | monitored |

Confusion matrix @ 0.32: TP=122 FP=20 FN=28 TN=430

Recall by fraud typology:

| Typology | Recall |
|---|---|
| account_takeover | 86.7% |
| card_testing | 90.0% |
| mule_transfer | 86.7% |
| stolen_card | 63.3% |
| structuring | 80.0% |

## §3.3 Stability & Reproducibility — PASS

| Test | Result | Tolerance |
|---|---|---|
| Repeat-run tier flip rate (3x on 50 txns) | 0.0% | 0% |
| Irrelevant-perturbation flip rate (memo/case edits) | 6.7% | <= 10% |

## §2.3 Real-Time Resilience — PASS

| Metric | Value | Target |
|---|---|---|
| Latency p50 | 0.1 ms | — |
| Latency p95 | 0.1 ms | — |
| Latency p99 | 0.1 ms | <= 300 ms |
| Throughput (single worker) | 10393 txn/s | — |
| Failover drill: fallback engagement | 100.0% | 100% |
| Failover drill: worst-case latency | 301 ms | <= 450 ms |

Failover drill forced the LLM dependency past the timeout budget on 20 live
transactions: every one was answered by the deterministic rules fallback,
flagged `degraded=true`, within budget + timeout margin. No transaction was dropped.

## §3.5 Adversarial Red Team — PASS

| Attack surface | Result | Required |
|---|---|---|
| Data-borne prompt injection (memo/merchant), 5 attacks | 100.0% neutralized, verdict unchanged | 100% |
| PII redaction before prompt (card/ID/phone) | 100.0% | 100% |
| Non-conforming LLM output never consumed (garbage + malicious JSON) | enforced | enforced |

## §3.4 Challenger Benchmark — PASS

| Model | AUC | Gini |
|---|---|---|
| Champion (LLM pipeline) | 0.948 | 0.896 |
| Challenger (deterministic rules) | 0.943 | 0.886 |

Champion Gini lift over rules baseline: **+0.010**. The rules
engine doubles as the runtime fallback, so degraded-mode discrimination equals
the challenger row above — acceptable for short outages, alert if fallback
rate exceeds 1% of traffic.

## §4 Input Drift Monitoring — operational

PSI between validation population and a shifted production-like sample
(alert threshold 0.25):

| Feature | PSI | Status |
|---|---|---|
| amount_inr | 0.189 | ok |
| hour_of_day_local | 0.412 | ALERT |
| txn_count_last_hour | 0.023 | ok |

Drift alerting fired correctly on the shifted sample —
the monitoring hook works; in production, an alert triggers golden-set re-evaluation.

## Findings & Conditions

1. **Condition:** re-run this harness against the pinned production LLM before
   go-live and on every base-model/prompt/guardrail version change (§4).
2. **Condition:** hard-legit false-positive rate (22.2%) must be
   tracked in production with customer-friction review; step-up auth (not block)
   is the mandated action for MEDIUM tier.
3. **Limitation:** golden set is synthetic; joint validation against confirmed-fraud
   labels required within 90 days of production data availability.
4. **Limitation:** injection lexicon is regex-based; add semantic injection
   screening off the hot path (async review queue) as a compensating control.
5. **Finding:** weakest typology is **stolen_card**
   at 63.3% recall — driven by "subtle" variants that
   avoid tripping strong signals; recommend targeted features (merchant risk list,
   domestic-BIN anomaly) before threshold relaxation.
6. **Finding:** champion Gini lift over the rules challenger is thin (+0.010);
   the LLM's value here is the reason narrative and long-tail patterns, not headline
   discrimination — re-assess lift against the production LLM (condition 1) before
   claiming discrimination benefit.

| Item | Result |
|---|---|
| Overall rating | **Approved with conditions** |
| Next validation due | 2026-10-15 or any version change, whichever is earlier |
