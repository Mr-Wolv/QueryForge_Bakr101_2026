#!/usr/bin/env python3
"""Generate benchmark graphs from k6 result JSONs in benchmark/results/.

Outputs:
  benchmark/results/dataset-scaling.png     (QF-000 dataset scaling)
  benchmark/results/index-before-after.png  (QF-001 baseline vs indexed)
  benchmark/results/throughput-plateau.png  (QF-003 concurrency ladder)
  benchmark/results/offset-vs-keyset.png    (QF-002 pagination depth comparison)
  benchmark/results/index-ordering-ab.png   (QF-004 index ordering A/B)

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
DEPTH_LABELS = ["0", "1,000", "10,000", "50,000"]

# One shared look across every figure.
BLUE = "#2563eb"    # latency p50 / keyset / order-first (the "winning" state)
RED = "#dc2626"     # latency p95 / offset / filter-first baseline
GREEN = "#059669"   # throughput / improved state
plt.rcParams.update({
    "font.size": 10,
    "axes.titlesize": 11.5,
    "axes.labelsize": 10,
    "legend.fontsize": 9.5,
    "axes.spines.top": False,
    "axes.spines.right": False,
    "axes.grid": True,
    "grid.alpha": 0.25,
    "grid.linestyle": "--",
    "grid.linewidth": 0.6,
    "legend.frameon": False,
    "xtick.labelsize": 9.5,
    "ytick.labelsize": 9.5,
    "figure.facecolor": "white",
    "savefig.facecolor": "white",
})


def load(name):
    with open(os.path.join(RESULTS, name + ".json"), encoding="utf-8") as f:
        return json.load(f)["metrics"]


def percentile(metric, key):
    return metric["values"][key]


def fmt_ms(v):
    """k6 summary JSONs store milliseconds; render them readably."""
    return f"{v:.0f} ms" if v < 1000 else f"{v / 1000:.2f} s"


def log_ms_ticks(ax, ticks):
    """Human-readable tick labels (ms / s) on a log latency axis."""
    ax.set_yticks(ticks)
    ax.set_yticklabels([fmt_ms(t) for t in ticks])
    ax.yaxis.set_minor_locator(matplotlib.ticker.NullLocator())


def main():
    # ---- Graph 0: QF-000 dataset scaling (baseline, no index) ----
    try:
        sizes = [10_000, 100_000, 1_000_000]
        runs = ["qf000-scaling-search-10K", "qf000-scaling-search-100K",
                "qf001-baseline-search-1M-r10"]
        sp50 = [percentile(load(r)["http_req_duration"], "p(50)") for r in runs]
        sp95 = [percentile(load(r)["http_req_duration"], "p(95)") for r in runs]
        fig, ax = plt.subplots(figsize=(8, 4.8))
        ax.plot(range(len(sizes)), sp95, "s-", color=RED, lw=2, markeredgewidth=0,
                markersize=7, zorder=2)
        ax.plot(range(len(sizes)), sp50, "o-", color=BLUE, lw=2, markeredgewidth=0,
                markersize=7, zorder=3)
        ax.set_yscale("log")
        ax.set_ylim(6, 20000)
        log_ms_ticks(ax, [10, 100, 1000, 10000])
        ax.set_xticks(range(len(sizes)), ["10K", "100K", "1M"])
        ax.set_xlabel("Dataset size (rows, seed=42, no index)")
        ax.set_ylabel("Latency (log scale)")
        ax.set_title("QF-000: filtered-search latency vs dataset size (baseline, no index)\n"
                     "same code: fits in cache at 100K — collapses by ~2 orders of magnitude at 1M")
        ax.annotate("p95", (2.06, sp95[-1]), color=RED, fontsize=10.5, fontweight="bold",
                    va="center")
        ax.annotate("p50", (2.06, sp50[-1]), color=BLUE, fontsize=10.5, fontweight="bold",
                    va="center")
        ax.set_xlim(-0.35, 2.55)
        fig.tight_layout()
        fig.savefig(os.path.join(RESULTS, "dataset-scaling.png"), dpi=130)
        print("wrote:", os.path.join(RESULTS, "dataset-scaling.png"))
    except FileNotFoundError:
        print("skipping QF-000 graph (missing result files)")

    # ---- Graph 0b: QF-001 before/after the composite index ----
    try:
        base = load("qf001-baseline-search-1M-r10")["http_req_duration"]["values"]
        idx = load("qf001-indexed-search-1M-r10")["http_req_duration"]["values"]
        groups = ["p50", "p95"]
        baseline = [base["p(50)"], base["p(95)"]]
        indexed = [idx["p(50)"], idx["p(95)"]]
        fig, ax = plt.subplots(figsize=(8, 4.8))
        xpos = range(len(groups))
        w = 0.36
        b1 = ax.bar([x - w / 2 for x in xpos], baseline, w, color=RED,
                    label="baseline (no secondary index)")
        b2 = ax.bar([x + w / 2 for x in xpos], indexed, w, color=GREEN,
                    label="indexed (idx_products_search)")
        for bars in (b1, b2):
            for b in bars:
                v = b.get_height()
                ax.annotate(fmt_ms(v), (b.get_x() + b.get_width() / 2, v),
                            xytext=(0, 5), textcoords="offset points", ha="center",
                            fontsize=10, fontweight="bold")
        ax.set_xticks(xpos, groups)
        ax.set_ylim(0, max(baseline) * 1.18)
        ax.set_ylabel("Latency (ms, 10 req/s offered)")
        ax.set_title("QF-001: filtered search before/after the workload-shaped index (1M rows)\n"
                     "p50 ~40×, p95 ~63× faster; envelope COUNT 79 ms → 11.4 ms")
        ax.legend()
        fig.tight_layout()
        fig.savefig(os.path.join(RESULTS, "index-before-after.png"), dpi=130)
        print("wrote:", os.path.join(RESULTS, "index-before-after.png"))
    except FileNotFoundError:
        print("skipping QF-001 graph (missing result files)")

    # ---- Graph 1: QF-003 concurrency ladder ----
    p50, p95, rps = [], [], []
    for v in VUS:
        m = load(f"qf003-vus{v}")
        p50.append(percentile(m["http_req_duration"], "p(50)"))
        p95.append(percentile(m["http_req_duration"], "p(95)"))
        rps.append(m["http_reqs"]["values"]["rate"])

    fig, ax1 = plt.subplots(figsize=(8, 4.8))
    ax1.plot(VUS, p95, "s-", color=RED, lw=1.6, markersize=5, alpha=0.55,
             markeredgewidth=0, label="p95 latency")
    ax1.plot(VUS, p50, "o-", color=BLUE, lw=2, markersize=7, markeredgewidth=0,
             label="p50 latency", zorder=3)
    ax1.set_yscale("log")
    ax1.set_ylim(150, 15000)
    log_ms_ticks(ax1, [200, 500, 1000, 2000, 5000, 10000])
    ax1.set_xlabel("Concurrent users (VUs)")
    ax1.set_ylabel("Latency (log scale)")
    ax2 = ax1.twinx()
    ax2.spines["right"].set_visible(True)
    ax2.plot(VUS, rps, "^--", color=GREEN, lw=1.8, label="throughput (req/s)")
    ax2.set_ylabel("Throughput (req/s)")
    ax2.set_ylim(0, max(rps) * 1.9)
    ax2.grid(False)
    lines1, labels1 = ax1.get_legend_handles_labels()
    lines2, labels2 = ax2.get_legend_handles_labels()
    ax1.legend(lines1 + lines2, labels1 + labels2, loc="upper left")
    ax1.set_title("QF-003: filtered search under increasing concurrency (1M rows, indexed)\n"
                  "flat throughput + linear latency = saturated connection pool")
    fig.tight_layout()
    fig.savefig(os.path.join(RESULTS, "throughput-plateau.png"), dpi=130)

    # ---- Graph 2: QF-002 offset vs keyset by depth (linear x: depth 0 included) ----
    fig, ax = plt.subplots(figsize=(8, 4.8))
    x = range(len(DEPTHS))
    for mode, color, marker in (("offset", RED, "o"), ("keyset", BLUE, "s")):
        m = load(f"qf002-{mode}-1M")
        ys = [percentile(m[f"{mode}_d{d}"], "p(50)") for d in DEPTHS]
        ys95 = [percentile(m[f"{mode}_d{d}"], "p(95)") for d in DEPTHS]
        ax.plot(x, ys, marker + "-", color=color, lw=2, label=f"{mode} p50")
        ax.plot(x, ys95, marker + "--", color=color, lw=1.4, alpha=0.45, label=f"{mode} p95")
    ax.set_xticks(x, DEPTH_LABELS)
    ax.set_ylim(0, 210)
    ax.set_xlabel("Page depth (rows skipped into the result set)")
    ax.set_ylabel("Latency (ms)")
    ax.set_title("QF-002: OFFSET vs KEYSET pagination by depth (1M rows, 20 req/s)")
    ax.legend(ncol=2)
    fig.tight_layout()
    fig.savefig(os.path.join(RESULTS, "offset-vs-keyset.png"), dpi=130)

    # ---- Graph 3: QF-004 index ordering A/B (filter-first vs order-first) ----
    try:
        kf = load("qf004-keyset-filter-first-only")
        ko = load("qf004-keyset-both-indexes")
        sf = load("qf004-search-filter-first-only-r10")
        so = load("qf004-search-both-indexes-r10")
        fig, (axa, axb) = plt.subplots(1, 2, figsize=(11, 4.6))

        # Categorical x: depth 0 exists, so a log axis would silently drop it.
        kf50 = [percentile(kf[f"keyset_d{d}"], "p(50)") for d in DEPTHS]
        ko50 = [percentile(ko[f"keyset_d{d}"], "p(50)") for d in DEPTHS]
        axa.plot(x, kf50, "o-", color=RED, lw=2, label="filter-first index only (shipped)")
        axa.plot(x, ko50, "s-", color=GREEN, lw=2, label="order-first index present")
        axa.set_xticks(x, DEPTH_LABELS)
        axa.set_ylim(0, 75)
        axa.set_xlabel("Page depth (rows behind the cursor)")
        axa.set_ylabel("keyset p50 latency (ms)")
        axa.set_title("Keyset: order-first turns the cursor\npredicate into a real index walk",
                      fontsize=10.5)
        axa.legend(loc="upper right")

        states = ["filter-first\nonly (shipped)", "order-first\npresent"]
        search_p50 = [percentile(sf["http_req_duration"], "p(50)"),
                      percentile(so["http_req_duration"], "p(50)")]
        bars = axb.bar(states, search_p50, color=[RED, GREEN], width=0.45)
        for b, v in zip(bars, search_p50):
            axb.annotate(f"{v:.1f} ms", (b.get_x() + b.get_width() / 2, v),
                         textcoords="offset points", xytext=(0, 5), ha="center",
                         fontsize=10, fontweight="bold")
        axb.set_ylim(0, max(search_p50) * 1.25)
        axb.set_ylabel("filtered search p50 (ms, 10 req/s)")
        axb.set_title("Filtered search: the planner picks the\nordered walk when the index exists",
                      fontsize=10.5)
        axb.grid(True, axis="x", alpha=0)

        fig.suptitle("QF-004: index column ordering — filter-first (category_id, status, price, "
                     "created_at DESC) vs order-first (category_id, status, created_at DESC, price)",
                     fontsize=10.5, y=0.99)
        fig.tight_layout(rect=(0, 0, 1, 0.90))
        fig.savefig(os.path.join(RESULTS, "index-ordering-ab.png"), dpi=130)
        print("wrote:", os.path.join(RESULTS, "index-ordering-ab.png"))
    except FileNotFoundError as e:
        print("skipping QF-004 graph (missing result files)", e)

    print("wrote:", os.path.join(RESULTS, "throughput-plateau.png"))
    print("wrote:", os.path.join(RESULTS, "offset-vs-keyset.png"))


if __name__ == "__main__":
    main()
