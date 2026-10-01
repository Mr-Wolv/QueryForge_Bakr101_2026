#!/usr/bin/env python3
"""Deterministic product dataset generator for QueryForge.

seed=42. Same N => same logical dataset on every machine/run (per-row RNG).

Default output: benchmark/seed/products_seed42.csv (with header, COPY-ready via load-data.sh).
Use --csv FILE PATH to write raw rows (no header) to a different path.
"""
import argparse
import csv
import random
import sys
from datetime import datetime, timedelta, timezone

SEED = 42
N_CATEGORIES = 5
STATUS_WEIGHTS = [("ACTIVE", 55), ("INACTIVE", 30), ("DISCONTINUED", 15)]
BASE_TS = datetime(2024, 1, 1, tzinfo=timezone.utc)


def row_rng(seed: int, i: int) -> random.Random:
    """Independent per-row RNG: row i is identical regardless of N or generation order."""
    return random.Random(f"{seed}:{i}")


def gen_row(i: int) -> tuple:
    r = row_rng(SEED, i)
    category = r.randrange(1, N_CATEGORIES + 1)
    price = r.randint(500, 50099) / 100.0          # 5.00 .. 500.99
    stock = r.randint(0, 1000)
    status = r.choices([s for s, _ in STATUS_WEIGHTS], [w for _, w in STATUS_WEIGHTS])[0]
    created = BASE_TS + timedelta(seconds=i)       # strictly increasing => deterministic keyset order
    updated = created + timedelta(hours=1)
    return (i, f"SKU{i:08d}", f"Product {i}", category, price, stock, status,
            created.isoformat(), updated.isoformat())


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("rows", nargs="?", type=int, default=10_000)
    ap.add_argument("--csv", dest="csv_path", default="benchmark/seed/products_seed42.csv",
                    help="output CSV path (with header)")
    args = ap.parse_args()

    if args.rows < 1:
        print("rows must be >= 1", file=sys.stderr)
        return 2

    with open(args.csv_path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["id", "sku", "name", "category_id", "price", "stock_quantity",
                    "status", "created_at", "updated_at"])
        for i in range(1, args.rows + 1):
            w.writerow(gen_row(i))

    print(f"Generated {args.rows} rows (seed={SEED}) -> {args.csv_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
