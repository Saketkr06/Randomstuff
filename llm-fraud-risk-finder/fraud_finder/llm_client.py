"""Pluggable LLM clients.

- AnthropicClient: production client (requires ANTHROPIC_API_KEY).
- OfflineReferenceClient: deterministic stand-in that emulates an instruction-tuned
  LLM assessing the same signals. It exists so the validation harness is fully
  reproducible with no network dependency — and it doubles as the challenger's
  sparring partner. It is intentionally imperfect (bounded noise, occasional
  borderline misreads) so validation metrics reflect a realistic model rather
  than an oracle.

Both expose: complete(system: str, user: str, timeout_s: float) -> str
"""

from __future__ import annotations

import hashlib
import json
import math
import os
import re
import time


class LLMTimeout(Exception):
    pass


class LLMError(Exception):
    pass


class AnthropicClient:
    """Thin wrapper over the Anthropic Messages API."""

    MODEL = "claude-sonnet-5"

    def __init__(self, api_key: str | None = None):
        try:
            import anthropic
        except ImportError as e:
            raise LLMError("pip install anthropic to use AnthropicClient") from e
        self._client = anthropic.Anthropic(api_key=api_key or os.environ["ANTHROPIC_API_KEY"])

    @property
    def version(self) -> str:
        return f"anthropic/{self.MODEL}"

    def complete(self, system: str, user: str, timeout_s: float = 2.0) -> str:
        import anthropic

        try:
            resp = self._client.messages.create(
                model=self.MODEL,
                max_tokens=400,
                temperature=0,
                system=system,
                messages=[{"role": "user", "content": user}],
                timeout=timeout_s,
            )
            return resp.content[0].text
        except anthropic.APITimeoutError as e:
            raise LLMTimeout(str(e)) from e
        except Exception as e:  # surfaced to the fallback path
            raise LLMError(str(e)) from e


class OfflineReferenceClient:
    """Deterministic LLM emulator for offline validation runs.

    Scores the same fraud signals the system prompt describes, then adds
    hash-seeded (reproducible) noise and a small borderline-confusion band to
    mimic real LLM imperfection. Also honors the prompt's security rules:
    imperative text inside data fields does not change its behavior.
    """

    NOISE_SCALE = 0.08
    version = "offline-reference/1.0.0"

    def complete(self, system: str, user: str, timeout_s: float = 2.0) -> str:
        m = re.search(r"```json\n(.*?)\n```", user, re.S)
        if not m:
            raise LLMError("no transaction payload found")
        txn = json.loads(m.group(1))

        score, reasons = self._assess(txn)
        tier = self._tier(score)
        action = {"LOW": "ALLOW", "MEDIUM": "STEP_UP_AUTH", "HIGH": "HOLD", "CRITICAL": "BLOCK"}[tier]
        return json.dumps(
            {
                "risk_score": round(score, 4),
                "risk_tier": tier,
                "reasons": reasons[:6],
                "recommended_action": action,
            }
        )

    # -- internals ----------------------------------------------------------

    def _assess(self, t: dict) -> tuple[float, list[str]]:
        logit = -2.6
        reasons: list[str] = []

        amount = float(t.get("amount_inr", 0))
        avg30 = max(float(t.get("avg_txn_amount_30d", 1)), 1.0)
        ratio = amount / avg30

        if ratio > 10:
            logit += 1.6
            reasons.append(f"amount is {ratio:.0f}x the 30-day average")
        elif ratio > 4:
            logit += 0.8
            reasons.append(f"amount is {ratio:.1f}x the 30-day average")

        vel = int(t.get("txn_count_last_hour", 0))
        if vel >= 8:
            logit += 1.7
            reasons.append(f"{vel} transactions in the last hour")
        elif vel >= 4:
            logit += 0.9
            reasons.append(f"elevated velocity: {vel} txns in the last hour")

        if t.get("new_device"):
            logit += 1.1
            reasons.append("transaction from a new device")
        if int(t.get("failed_auth_attempts_24h", 0)) >= 3:
            logit += 1.3
            reasons.append("multiple failed auth attempts in 24h")
        if t.get("new_beneficiary") and amount > 2 * avg30:
            logit += 1.0
            reasons.append("high-value transfer to a first-time beneficiary")

        geo = float(t.get("geo_distance_from_home_km", 0))
        if geo > 2000 or (t.get("country") not in ("IN", None, "")):
            logit += 0.9
            reasons.append("transaction far from home location")

        if amount < 200 and t.get("channel") == "CARD_ECOM" and vel >= 4:
            logit += 1.2
            reasons.append("card-testing pattern: rapid small e-com charges")

        if 44000 <= amount <= 49999 and vel >= 2:
            logit += 1.1
            reasons.append("repeated amounts just below the reporting threshold")

        hour = int(t.get("hour_of_day_local", 12))
        if hour < 5 and ratio > 2:
            logit += 0.5
            reasons.append("unusual-hour high-value transaction")

        tenure = int(t.get("customer_tenure_months", 0))
        if tenure < 3 and ratio > 3:
            logit += 0.6
            reasons.append("new account with atypical spend")
        elif tenure > 60:
            logit -= 0.4

        score = 1 / (1 + math.exp(-logit))
        score = min(max(score + self._noise(t), 0.0), 1.0)
        if not reasons:
            reasons.append("no elevated risk signals; consistent with customer profile")
        return score, reasons

    def _noise(self, t: dict) -> float:
        seed = hashlib.sha256(json.dumps(t, sort_keys=True).encode()).digest()
        u = int.from_bytes(seed[:8], "big") / 2**64  # uniform [0,1), reproducible
        return (u - 0.5) * 2 * self.NOISE_SCALE

    @staticmethod
    def _tier(score: float) -> str:
        if score >= 0.85:
            return "CRITICAL"
        if score >= 0.65:
            return "HIGH"
        if score >= 0.35:
            return "MEDIUM"
        return "LOW"


class SlowClient:
    """Failover-drill helper: wraps a client and forces latency past the budget."""

    def __init__(self, inner, delay_s: float = 5.0):
        self._inner = inner
        self._delay = delay_s
        self.version = f"slow({inner.version})"

    def complete(self, system: str, user: str, timeout_s: float = 2.0) -> str:
        if self._delay > timeout_s:
            time.sleep(timeout_s)
            raise LLMTimeout(f"exceeded {timeout_s}s budget")
        time.sleep(self._delay)
        return self._inner.complete(system, user, timeout_s)
