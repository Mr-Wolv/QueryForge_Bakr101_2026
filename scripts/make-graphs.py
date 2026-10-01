#!/usr/bin/env python3
"""Generate benchmark graphs from k6 result JSONs in benchmark/results/.

Outputs:
  benchmark/results/throughput-plateau.png  (QF-003 concurrency ladder)
  benchmark/results/offset-vs-keyset.png    (QF-002 pagination depth comparison)

Requires: pip install matplotlib
"""
import json
import os

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

HERE = os.path.dirname(os.path.abspath(__file__))
RESULTS = os.path.join(HERE, "..", "benchmark", "results")

VUS = [10, 25, 50, 100, 200]
DEPTHS = [0, 1000, 10000, 50000]


def load(name):
    with open(os.path.join(RESULTS, name + ".json"), encoding="utf-8") as f:
        return json.load(f)["metrics"]


def percentile(metric, key):
    return metric["values"][key]


def main():
    # ---- Graph 0: QF-000 dataset scaling (baseline, no index) ----
    try:
        sizes = [10_000, 100_000, 1_000_000]
        runs = ["qf000-scaling-search-10K", "qf000-scaling-search-100K",
                "qf001-baseline-search-1M-r10"]
        sp50 = [percentile(load(r)["http_req_duration"], "p(50)") * 1000 for r in runs]
        sp95 = [percentile(load(r)["http_req_duration"], "p(95)") * 1000 for r in runs]
        fig, ax = plt.subplots(figsize=(8, 5))
        ax.plot(range(len(sizes)), sp50, "o-", label="p50 (ms)")
        ax.plot(range(len(sizes)), sp95, "s-", label="p95 (ms)")
        ax.set_xticks(range(len(sizes)), ["10K", "100K", "1M"])
        ax.set_yscale("log")
        ax.set_xlabel("Dataset size (rows, seed=42, no index)")
        ax.set_ylabel("Latency (ms, log scale)")
        ax.set_title("QF-000: filtered-search latency vs dataset size (baseline)\n"
                      "same code: fits-in-cache at 100K, collapses at 1M")
        for i, (a, b) in enumerate(zip(sp50, sp95)):
            ax.annotate(f"{a:.0f} ms", (i, a), textcoords="offset points", xytext=(0, 8), ha="center", fontsize=9)
            ax.annotate(f"{b:.0f} ms", (i, b), textcoords="offset points", xytext=(0, -14), ha="center", fontsize=9)
        ax.legend()
        ax.grid(True, alpha=0.3)
        fig.tight_layout()
        fig.savefig(os.path.join(RESULTS, "dataset-scaling.png"), dpi=130)
        print("wrote:", os.path.join(RESULTS, "dataset-scaling.png"))
    except FileNotFoundError:
        print("skipping QF-000 graph (missing result files)")

    # ---- Graph 1: QF-003 concurrency ladder ----
    p50, p95, rps = [], [], []
    for v in VUS:
        m = load(f"qf003-vus{v}")
        p50.append(percentile(m["http_req_duration"], "p(50)"))
        p95.append(percentile(m["http_req_duration"], "p(95)"))
        rps.append(m["http_reqs"]["values"]["rate"])

    fig, ax1 = plt.subplots(figsize=(8, 5))
    ax1.plot(VUS, [x * 1000 for x in p50], "o-", label="p50 latency (ms)")
    ax1.plot(VUS, [x * 1000 for x in p95], "s-", label="p95 latency (ms)")
    ax1.set_xlabel("Concurrent users (VUs)")
    ax1.set_ylabel("Latency (ms)")
    ax1.set_yscale("log")
    ax1.grid(True, alpha=0.3)
    ax2 = ax1.twinx()
    ax2.plot(VUS, rps, "^--", color="crimson", label="throughput (req/s)")
    ax2.set_ylabel("Throughput (req/s)")
    ax2.set_ylim(0, max(rps) * 1.6)
    lines1, labels1 = ax1.get_legend_handles_labels()
    lines2, labels2 = ax2.get_legend_handles_labels()
    ax1.legend(lines1 + lines2, labels1 + labels2, loc="upper left")
    ax1.set_title("QF-003: filtered search under increasing concurrency (1M rows, indexed)\n"
                  "flat throughput + linear latency = saturated connection pool")
    fig.tight_layout()
    fig.savefig(os.path.join(RESULTS, "throughput-plateau.png"), dpi=130)

    # ---- Graph 2: QF-002 offset vs keyset by depth ----
    fig, ax = plt.subplots(figsize=(8, 5))
    for mode in ("offset", "keyset"):
        m = load(f"qf002-{mode}-1M")
        ys = [percentile(m[f"{mode}_d{d}"], "p(50)") for d in DEPTHS]
        ax.plot(DEPTHS, ys, "o-" if mode == "offset" else "s-", label=f"{mode} p50")
        ys95 = [percentile(m[f"{mode}_d{d}"], "p(95)") for d in DEPTHS]
        ax.plot(DEPTHS, ys95, "o--" if mode == "offset" else "s--", alpha=0.45, label=f"{mode} p95")
    ax.set_xlabel("Page depth (rows skipped into the result set)")
    ax.set_ylabel("Latency (ms)")
    ax.set_title("QF-002: OFFSET vs KEYSET pagination by depth (1M rows, 20 req/s)")
    ax.legend()
    ax.grid(True, alpha=0.3)
    fig.tight_layout()
    fig.savefig(os.path.join(RESULTS, "offset-vs-keyset.png"), dpi=130)

    print("wrote:", os.path.join(RESULTS, "throughput-plateau.png"))
    print("wrote:", os.path.join(RESULTS, "offset-vs-keyset.png"))


if __name__ == "__main__":
    main()
