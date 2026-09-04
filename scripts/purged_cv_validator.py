"""
M4P2S1: Information Interval Overlap Purging & Embargo Walk-Forward Validator

Objective (README.md, M4P2S1):
    Purge training samples overlapping test intervals [T_k - L, T_k] and
    [T_k, T_k + tau].
Verification (README.md, M4P2S1):
    Asserts zero information overlap between training folds and
    out-of-sample evaluation folds.
"""

from dataclasses import dataclass
from typing import List, Tuple
import numpy as np


@dataclass
class Interval:
    start: float
    end: float


def feature_lookback_interval(t_k, lookback_L):
    return Interval(t_k - lookback_L, t_k)


def label_interval(t_k, tau):
    return Interval(t_k, t_k + tau)


def intervals_overlap(a, b):
    return a.start <= b.end and b.start <= a.end


def purge_training_samples(train_timestamps, test_start, test_end, lookback_L, tau, embargo=0.0):
    zone = Interval(test_start - embargo, test_end + embargo)
    keep_mask = np.ones(len(train_timestamps), dtype=bool)
    for i, t_k in enumerate(train_timestamps):
        feat_iv = feature_lookback_interval(t_k, lookback_L)
        lbl_iv = label_interval(t_k, tau)
        if intervals_overlap(feat_iv, zone) or intervals_overlap(lbl_iv, zone):
            keep_mask[i] = False
    return keep_mask


def generate_walk_forward_folds(all_timestamps, n_folds, lookback_L, tau, embargo):
    n = len(all_timestamps)
    if n_folds < 1 or n < (n_folds + 1):
        raise ValueError("Not enough timestamps for the requested n_folds")

    test_block_size = n // (n_folds + 1)
    folds = []

    for i in range(n_folds):
        test_start_idx = (i + 1) * test_block_size
        test_end_idx = n if i == n_folds - 1 else test_start_idx + test_block_size

        test_mask = np.zeros(n, dtype=bool)
        test_mask[test_start_idx:test_end_idx] = True

        candidate_train_mask = np.zeros(n, dtype=bool)
        candidate_train_mask[:test_start_idx] = True

        test_start_val = all_timestamps[test_start_idx]
        test_end_val = all_timestamps[test_end_idx - 1]

        purge_mask = purge_training_samples(
            all_timestamps, test_start_val, test_end_val, lookback_L, tau, embargo
        )

        final_train_mask = candidate_train_mask & purge_mask
        folds.append((final_train_mask, test_mask))

    return folds


def assert_zero_overlap(train_timestamps, train_mask, test_start, test_end, lookback_L, tau, embargo):
    zone = Interval(test_start - embargo, test_end + embargo)
    n_total = len(train_timestamps)
    n_kept = int(train_mask.sum())
    violations = 0
    violation_details = []

    for i, t_k in enumerate(train_timestamps):
        if not train_mask[i]:
            continue
        feat_iv = feature_lookback_interval(t_k, lookback_L)
        lbl_iv = label_interval(t_k, tau)
        if intervals_overlap(feat_iv, zone) or intervals_overlap(lbl_iv, zone):
            violations += 1
            detail = "t_k=" + str(t_k) + " feat=[" + str(feat_iv.start) + "," + str(feat_iv.end) + \
                     "] label=[" + str(lbl_iv.start) + "," + str(lbl_iv.end) + \
                     "] zone=[" + str(zone.start) + "," + str(zone.end) + "]"
            violation_details.append(detail)

    if violations > 0:
        raise AssertionError(
            str(violations) + " kept training sample(s) overlap the test zone:\n"
            + "\n".join(violation_details[:5])
        )

    return {
        "n_total": n_total,
        "n_kept": n_kept,
        "n_purged": n_total - n_kept,
        "overlap_violations": 0,
    }


if __name__ == "__main__":
    timestamps = np.arange(0, 100_000_000_000, 1_000_000_000, dtype=np.float64)
    lookback_L = 5_000_000_000
    tau = 5_000_000_000
    embargo = 2_000_000_000
    test_start = 50_000_000_000.0
    test_end = 60_000_000_000.0

    mask = purge_training_samples(timestamps, test_start, test_end, lookback_L, tau, embargo)
    result = assert_zero_overlap(timestamps, mask, test_start, test_end, lookback_L, tau, embargo)
    print("n_total=" + str(result["n_total"]) + " n_kept=" + str(result["n_kept"]) +
          " n_purged=" + str(result["n_purged"]) + " overlap_violations=" + str(result["overlap_violations"]))

    edge_case_ts = np.array([test_start - lookback_L - 0.001], dtype=np.float64)
    edge_mask = purge_training_samples(edge_case_ts, test_start, test_end, lookback_L, tau, embargo)
    assert edge_mask[0] == False, (
        "Edge-case sample whose label interval just barely touches the "
        "embargo zone was NOT purged -- overlap check is likely using "
        "strict containment instead of any-overlap logic."
    )
    print("Edge-case overlap test passed.")

    folds = generate_walk_forward_folds(timestamps, n_folds=3, lookback_L=lookback_L, tau=tau, embargo=embargo)
    for i, (train_mask, test_mask) in enumerate(folds):
        test_idx = np.where(test_mask)[0]
        fold_result = assert_zero_overlap(
            timestamps, train_mask, timestamps[test_idx[0]], timestamps[test_idx[-1]],
            lookback_L, tau, embargo
        )
        print("Fold " + str(i) + ": train_kept=" + str(fold_result["n_kept"]) +
              " test_size=" + str(int(test_mask.sum())) + " violations=" + str(fold_result["overlap_violations"]))
