"""FraudRiskFinder: the real-time scoring pipeline.

Flow per transaction (hot path, budgeted):
  input guardrail -> prompt build -> LLM call (timeout) -> output guardrail
  -> RiskAssessment
Any failure past the input guardrail routes to the deterministic rules
fallback, so the system always answers within budget and never consumes an
unvalidated LLM output.
"""

from __future__ import annotations

import json
import time

from . import guardrails
from .llm_client import LLMError, LLMTimeout
from .prompts import PROMPT_VERSION, SYSTEM_PROMPT, build_user_prompt
from .schemas import (
    RiskAssessment,
    Transaction,
    action_for_tier,
    parse_llm_json,
    tier_for_score,
)

MODEL_VERSION = "fraud-risk-finder/1.0.0"


class FraudRiskFinder:
    def __init__(self, llm_client, latency_budget_s: float = 2.0):
        self._llm = llm_client
        self._budget = latency_budget_s
        self.audit_log: list[dict] = []

    def score(self, txn: Transaction) -> RiskAssessment:
        start = time.perf_counter()
        version = f"{MODEL_VERSION}|{PROMPT_VERSION}|{self._llm.version}"

        gate = guardrails.sanitize_input(txn)
        if not gate.ok:
            # Malformed input: refuse to score rather than guess.
            result = RiskAssessment(
                txn_id=txn.txn_id,
                risk_score=1.0,
                risk_tier="CRITICAL",
                reasons=[f"input rejected: {v}" for v in gate.violations or []],
                recommended_action="HOLD",
                degraded=True,
                model_version=version,
            )
            return self._finish(result, start, gate, "input_rejected")

        clean = gate.sanitized
        try:
            raw = self._llm.complete(
                SYSTEM_PROMPT,
                build_user_prompt(json.dumps(clean.to_prompt_dict())),
                timeout_s=self._budget,
            )
            payload = parse_llm_json(raw)
            if payload is None:
                raise LLMError("unparseable LLM output")
            violations = guardrails.validate_output(payload)
            if violations:
                raise LLMError(f"output contract violations: {violations}")

            result = RiskAssessment(
                txn_id=txn.txn_id,
                risk_score=float(payload["risk_score"]),
                risk_tier=payload["risk_tier"],
                reasons=list(payload["reasons"]),
                recommended_action=payload["recommended_action"],
                degraded=False,
                model_version=version,
            )
            return self._finish(result, start, gate, "llm")
        except (LLMTimeout, LLMError) as e:
            result = self._fallback(clean, version)
            result.reasons.append(f"fallback engaged: {type(e).__name__}")
            return self._finish(result, start, gate, "fallback")

    # -- deterministic fallback (rules) -------------------------------------

    @staticmethod
    def _fallback(t: Transaction, version: str) -> RiskAssessment:
        """Conservative rules score used when the LLM path fails. Also serves
        as the challenger baseline in validation."""
        score = 0.05
        reasons = []
        ratio = t.amount_inr / max(t.avg_txn_amount_30d, 1.0)
        if ratio > 10:
            score += 0.35
            reasons.append("rule: amount >10x 30-day average")
        elif ratio > 4:
            score += 0.20
            reasons.append("rule: amount >4x 30-day average")
        if t.txn_count_last_hour >= 8:
            score += 0.30
            reasons.append("rule: extreme velocity")
        elif t.txn_count_last_hour >= 4:
            score += 0.15
            reasons.append("rule: elevated velocity")
        if t.new_device:
            score += 0.15
            reasons.append("rule: new device")
        if t.failed_auth_attempts_24h >= 3:
            score += 0.20
            reasons.append("rule: failed auth attempts")
        if t.new_beneficiary and ratio > 2:
            score += 0.15
            reasons.append("rule: new beneficiary high value")
        if t.geo_distance_from_home_km > 2000 or t.country != "IN":
            score += 0.10
            reasons.append("rule: unusual location")
        score = min(score, 1.0)
        tier = tier_for_score(score)
        return RiskAssessment(
            txn_id=t.txn_id,
            risk_score=round(score, 4),
            risk_tier=tier,
            reasons=reasons or ["rule: no elevated signals"],
            recommended_action=action_for_tier(tier),
            degraded=True,
            model_version=version,
        )

    # -- observability -------------------------------------------------------

    def _finish(self, result, start, gate, path: str) -> RiskAssessment:
        result.latency_ms = round((time.perf_counter() - start) * 1000, 2)
        self.audit_log.append(
            {
                "txn_id": result.txn_id,
                "path": path,
                "injection_detected": bool(gate.injection_detected),
                "risk_score": result.risk_score,
                "risk_tier": result.risk_tier,
                "action": result.recommended_action,
                "degraded": result.degraded,
                "latency_ms": result.latency_ms,
                "model_version": result.model_version,
            }
        )
        return result
