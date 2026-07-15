"""Input/output guardrails. These run inside the real-time budget, so they are
regex/schema checks only — no secondary model calls on the hot path."""

from __future__ import annotations

import re
from dataclasses import dataclass

from .schemas import RISK_TIERS, Transaction

# Imperative patterns that indicate data-borne prompt injection. Free-text fields
# (merchant, memo) are attacker-controllable via merchant onboarding or UPI notes.
_INJECTION_PATTERNS = [
    r"ignore (all |any |previous |prior )*(instructions|rules)",
    r"disregard .{0,30}(instructions|rules|system)",
    r"you are now",
    r"act as ",
    r"system prompt",
    r"reveal .{0,30}(prompt|instructions)",
    r"respond with",
    r"set +risk[_ ]?score",
    r"mark .{0,20}(low|safe)",
    r"override",
    r"\bAI\b.{0,20}(assistant|model).{0,30}(must|should)",
]
_INJECTION_RE = re.compile("|".join(_INJECTION_PATTERNS), re.IGNORECASE)

# PAN-like card numbers, Aadhaar-like 12-digit groups, Indian mobile numbers.
_PII_RES = [
    (re.compile(r"\b\d{4}[- ]?\d{4}[- ]?\d{4}[- ]?\d{2,4}\b"), "[REDACTED_CARD]"),
    (re.compile(r"\b\d{4}\s\d{4}\s\d{4}\b"), "[REDACTED_ID]"),
    (re.compile(r"\b[6-9]\d{9}\b"), "[REDACTED_PHONE]"),
]

_FREE_TEXT_MAX = 140
_VALID_CHANNELS = {"UPI", "CARD_POS", "CARD_ECOM", "NETBANKING", "ATM"}


@dataclass
class GuardrailResult:
    ok: bool
    sanitized: Transaction | None = None
    violations: list[str] | None = None
    injection_detected: bool = False


def sanitize_input(txn: Transaction) -> GuardrailResult:
    """Validate the schema and sanitize attacker-controllable free text."""
    violations: list[str] = []
    if txn.amount_inr <= 0 or txn.amount_inr > 1e9:
        violations.append("amount_inr out of range")
    if txn.channel not in _VALID_CHANNELS:
        violations.append(f"unknown channel {txn.channel!r}")
    if not txn.txn_id:
        violations.append("missing txn_id")
    if txn.txn_count_last_hour < 0 or txn.customer_tenure_months < 0:
        violations.append("negative context feature")
    if violations:
        return GuardrailResult(ok=False, violations=violations)

    injection = False
    merchant, memo = txn.merchant, txn.memo
    for name, value in (("merchant", merchant), ("memo", memo)):
        if _INJECTION_RE.search(value or ""):
            injection = True
    # Neutralize regardless: strip injected imperatives, redact PII, cap length.
    merchant = _neutralize(merchant)
    memo = _neutralize(memo)

    clean = Transaction(**{**txn.to_prompt_dict(), "merchant": merchant, "memo": memo})
    return GuardrailResult(ok=True, sanitized=clean, injection_detected=injection)


def _neutralize(text: str) -> str:
    text = (text or "")[:_FREE_TEXT_MAX]
    text = _INJECTION_RE.sub("[REMOVED]", text)
    for pattern, repl in _PII_RES:
        text = pattern.sub(repl, text)
    # Backticks/braces could break out of the JSON fence in the prompt.
    return text.replace("`", "'").replace("{", "(").replace("}", ")")


def validate_output(payload: dict) -> list[str]:
    """Enforce the RiskAssessment output contract; returns a list of violations."""
    violations = []
    score = payload.get("risk_score")
    if not isinstance(score, (int, float)) or not (0 <= float(score) <= 1):
        violations.append("risk_score missing or outside [0,1]")
    if payload.get("risk_tier") not in RISK_TIERS:
        violations.append("risk_tier not a valid enum value")
    if payload.get("recommended_action") not in ("ALLOW", "STEP_UP_AUTH", "HOLD", "BLOCK"):
        violations.append("recommended_action invalid")
    reasons = payload.get("reasons")
    if not isinstance(reasons, list) or not all(isinstance(r, str) for r in reasons):
        violations.append("reasons must be a list of strings")
    elif len(reasons) > 6:
        violations.append("too many reasons")
    return violations
