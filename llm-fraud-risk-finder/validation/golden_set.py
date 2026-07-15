"""Golden dataset generator: labeled synthetic transactions covering the fraud
typologies the model claims to detect (checklist §1.4). Seeded for reproducibility.

Typologies:
  legit            — ordinary spend consistent with the customer profile
  hard_legit       — legitimate but unusual (travel, festival splurge): FP pressure
  account_takeover — new device + failed auths + geo displacement + drain
  card_testing     — burst of small e-com charges
  stolen_card      — high value, foreign/far, unusual hour
  structuring      — repeated just-below-threshold transfers
  mule_transfer    — new account, new beneficiary, pass-through amounts
"""

from __future__ import annotations

import random
import sys
from dataclasses import dataclass

sys.path.insert(0, __file__.rsplit("/", 2)[0])
from fraud_finder.schemas import Transaction  # noqa: E402

MERCHANTS_LEGIT = [
    ("Swiggy", "FOOD"), ("BigBasket", "GROCERY"), ("Amazon IN", "ECOM"),
    ("Indian Oil", "FUEL"), ("BESCOM", "UTILITY"), ("PVR Cinemas", "ENTERTAINMENT"),
    ("Apollo Pharmacy", "PHARMA"), ("IRCTC", "TRAVEL"),
]
MERCHANTS_RISKY = [
    ("QUICKGEMS LLC", "JEWELLERY"), ("LuxWatch Intl", "LUXURY"),
    ("CryptoRamp", "CRYPTO"), ("GiftCardHub", "GIFTCARD"), ("BetPlay 247", "GAMING"),
]


@dataclass
class LabeledTxn:
    txn: Transaction
    is_fraud: int
    typology: str


def _base_customer(rng: random.Random) -> dict:
    return {
        "customer_tenure_months": rng.randint(6, 120),
        "avg_txn_amount_30d": rng.choice([800, 1500, 3000, 6000, 12000]),
        "txn_count_last_hour": rng.randint(0, 2),
        "distinct_merchants_last_hour": rng.randint(0, 2),
        "new_device": False,
        "new_beneficiary": False,
        "geo_distance_from_home_km": rng.uniform(0, 30),
        "failed_auth_attempts_24h": 0,
        "hour_of_day_local": rng.randint(8, 22),
        "country": "IN",
    }


def _mk(rng, i, typology, **over) -> Transaction:
    base = _base_customer(rng)
    merchant, cat = rng.choice(MERCHANTS_LEGIT)
    fields = {
        "txn_id": f"T{i:06d}",
        "amount_inr": round(base["avg_txn_amount_30d"] * rng.uniform(0.3, 1.8), 2),
        "merchant": merchant,
        "merchant_category": cat,
        "channel": rng.choice(["UPI", "CARD_POS", "CARD_ECOM"]),
        "timestamp_utc": f"2026-07-{rng.randint(1, 14):02d}T{rng.randint(0, 23):02d}:00:00Z",
        "memo": "",
        **base,
    }
    fields.update(over)
    return Transaction(**fields)


def generate(n: int = 600, fraud_rate: float = 0.25, seed: int = 42) -> list[LabeledTxn]:
    """Fraud is oversampled vs production so tail metrics are estimable."""
    rng = random.Random(seed)
    n_fraud = int(n * fraud_rate)
    n_legit = n - n_fraud
    out: list[LabeledTxn] = []
    i = 0

    for _ in range(int(n_legit * 0.8)):
        out.append(LabeledTxn(_mk(rng, i, "legit"), 0, "legit")); i += 1

    # Hard legits: the false-positive stress segment — legitimate behavior that
    # trips fraud signals (big travel spend, new phone, transacting abroad).
    for _ in range(n_legit - int(n_legit * 0.8)):
        avg = rng.choice([1500, 3000, 6000])
        abroad = rng.random() < 0.3
        out.append(LabeledTxn(_mk(
            rng, i, "hard_legit",
            amount_inr=round(avg * rng.uniform(3, 9), 2),
            merchant_category="TRAVEL",
            merchant=rng.choice(["MakeMyTrip", "Air India", "Taj Hotels"]),
            geo_distance_from_home_km=rng.uniform(200, 4000) if abroad else rng.uniform(200, 1500),
            country=rng.choice(["AE", "SG", "US"]) if abroad else "IN",
            new_device=rng.random() < 0.25,
            new_beneficiary=rng.random() < 0.3,
            hour_of_day_local=rng.choice([2, 3, 9, 14, 21]),
            avg_txn_amount_30d=avg,
            customer_tenure_months=rng.randint(36, 140),
        ), 0, "hard_legit")); i += 1

    # ~30% of each fraud typology is "subtle": careful fraudsters who avoid
    # tripping every signal at once. These are the cases that cost AUC.
    per = n_fraud // 5
    for _ in range(per):
        avg = rng.choice([1500, 3000, 6000])
        subtle = rng.random() < 0.3
        out.append(LabeledTxn(_mk(
            rng, i, "account_takeover",
            amount_inr=round(avg * (rng.uniform(2, 5) if subtle else rng.uniform(8, 25)), 2),
            new_device=not subtle or rng.random() < 0.5,
            new_beneficiary=True,
            failed_auth_attempts_24h=rng.randint(0, 1) if subtle else rng.randint(3, 8),
            geo_distance_from_home_km=rng.uniform(0, 100) if subtle else rng.uniform(500, 4000),
            channel="NETBANKING", avg_txn_amount_30d=avg,
            hour_of_day_local=rng.randint(9, 18) if subtle else rng.randint(0, 4),
        ), 1, "account_takeover")); i += 1

    for _ in range(per):
        subtle = rng.random() < 0.3
        out.append(LabeledTxn(_mk(
            rng, i, "card_testing",
            amount_inr=round(rng.uniform(10, 150), 2),
            channel="CARD_ECOM",
            merchant=rng.choice(MERCHANTS_RISKY)[0],
            merchant_category="ECOM",
            txn_count_last_hour=rng.randint(3, 5) if subtle else rng.randint(6, 20),
            distinct_merchants_last_hour=rng.randint(2, 3) if subtle else rng.randint(4, 12),
            new_device=rng.random() < 0.5,
        ), 1, "card_testing")); i += 1

    for _ in range(per):
        m, c = rng.choice(MERCHANTS_RISKY)
        avg = rng.choice([1500, 3000, 6000])
        subtle = rng.random() < 0.3
        out.append(LabeledTxn(_mk(
            rng, i, "stolen_card",
            amount_inr=round(avg * (rng.uniform(3, 6) if subtle else rng.uniform(10, 40)), 2),
            merchant=m, merchant_category=c, channel="CARD_ECOM",
            country="IN" if subtle else rng.choice(["AE", "US", "SG", "HK"]),
            geo_distance_from_home_km=rng.uniform(50, 400) if subtle else rng.uniform(2500, 9000),
            hour_of_day_local=rng.randint(10, 20) if subtle else rng.randint(1, 4),
            avg_txn_amount_30d=avg,
        ), 1, "stolen_card")); i += 1

    for _ in range(per):
        subtle = rng.random() < 0.3
        out.append(LabeledTxn(_mk(
            rng, i, "structuring",
            amount_inr=float(rng.randint(38000, 44000)) if subtle else float(rng.randint(44500, 49900)),
            channel="NETBANKING", new_beneficiary=True,
            txn_count_last_hour=1 if subtle else rng.randint(2, 5),
            avg_txn_amount_30d=rng.choice([3000, 6000, 12000]),
        ), 1, "structuring")); i += 1

    for _ in range(n_fraud - 4 * per):
        subtle = rng.random() < 0.3
        out.append(LabeledTxn(_mk(
            rng, i, "mule_transfer",
            amount_inr=round(rng.uniform(4000, 15000) if subtle else rng.uniform(15000, 90000), 2),
            channel="UPI", new_beneficiary=True,
            customer_tenure_months=rng.randint(4, 12) if subtle else rng.randint(0, 2),
            avg_txn_amount_30d=rng.choice([500, 900, 1400]) if not subtle else rng.choice([1500, 3000]),
            txn_count_last_hour=rng.randint(0, 2) if subtle else rng.randint(3, 7),
        ), 1, "mule_transfer")); i += 1

    rng.shuffle(out)
    return out


def production_shifted_sample(n: int = 400, seed: int = 7) -> list[LabeledTxn]:
    """A deliberately drifted population (higher amounts, more e-com, later hours)
    for the input-drift PSI check (checklist §4)."""
    sample = generate(n=n, fraud_rate=0.25, seed=seed)
    rng = random.Random(seed + 1)
    for lt in sample:
        lt.txn.amount_inr = round(lt.txn.amount_inr * rng.uniform(1.3, 2.0), 2)
        if rng.random() < 0.4:
            lt.txn.channel = "CARD_ECOM"
        lt.txn.hour_of_day_local = min(23, lt.txn.hour_of_day_local + rng.randint(0, 4))
    return sample
