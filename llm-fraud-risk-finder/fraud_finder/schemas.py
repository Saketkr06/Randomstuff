"""Data contracts for the fraud risk finder.

The Transaction is the model input; RiskAssessment is the validated output
contract. Anything the LLM returns that does not parse into RiskAssessment
is rejected by the output guardrail and routed to the fallback scorer.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field, asdict
from typing import Optional

RISK_TIERS = ("LOW", "MEDIUM", "HIGH", "CRITICAL")

# Score thresholds mapping to tiers (operating points set during validation).
TIER_THRESHOLDS = {"LOW": 0.0, "MEDIUM": 0.35, "HIGH": 0.65, "CRITICAL": 0.85}


@dataclass
class Transaction:
    """A single payment event plus the customer context available at scoring time."""

    txn_id: str
    amount_inr: float
    merchant: str
    merchant_category: str
    channel: str  # UPI | CARD_POS | CARD_ECOM | NETBANKING | ATM
    timestamp_utc: str
    country: str
    memo: str = ""

    # Customer context (from feature store, point-in-time correct)
    customer_tenure_months: int = 0
    avg_txn_amount_30d: float = 0.0
    txn_count_last_hour: int = 0
    distinct_merchants_last_hour: int = 0
    new_device: bool = False
    new_beneficiary: bool = False
    geo_distance_from_home_km: float = 0.0
    failed_auth_attempts_24h: int = 0
    hour_of_day_local: int = 12

    def to_prompt_dict(self) -> dict:
        return asdict(self)


@dataclass
class RiskAssessment:
    """Validated model output. This is the only shape downstream systems consume."""

    txn_id: str
    risk_score: float  # [0, 1]
    risk_tier: str  # one of RISK_TIERS
    reasons: list[str] = field(default_factory=list)
    recommended_action: str = "ALLOW"  # ALLOW | STEP_UP_AUTH | HOLD | BLOCK
    degraded: bool = False  # True when the deterministic fallback produced this
    model_version: str = ""
    latency_ms: float = 0.0

    @staticmethod
    def output_json_schema() -> dict:
        """The contract the LLM must satisfy (enforced by guardrails.validate_output)."""
        return {
            "type": "object",
            "required": ["risk_score", "risk_tier", "reasons", "recommended_action"],
            "properties": {
                "risk_score": {"type": "number", "minimum": 0, "maximum": 1},
                "risk_tier": {"enum": list(RISK_TIERS)},
                "reasons": {"type": "array", "items": {"type": "string"}, "maxItems": 6},
                "recommended_action": {"enum": ["ALLOW", "STEP_UP_AUTH", "HOLD", "BLOCK"]},
            },
        }


def tier_for_score(score: float) -> str:
    tier = "LOW"
    for name, floor in TIER_THRESHOLDS.items():
        if score >= floor:
            tier = name
    return tier


def action_for_tier(tier: str) -> str:
    return {
        "LOW": "ALLOW",
        "MEDIUM": "STEP_UP_AUTH",
        "HIGH": "HOLD",
        "CRITICAL": "BLOCK",
    }[tier]


def parse_llm_json(raw: str) -> Optional[dict]:
    """Extract the first JSON object from an LLM response; None if unparseable."""
    raw = raw.strip()
    start = raw.find("{")
    end = raw.rfind("}")
    if start == -1 or end <= start:
        return None
    try:
        return json.loads(raw[start : end + 1])
    except json.JSONDecodeError:
        return None
