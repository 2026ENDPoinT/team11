"""Configurable product policy; band values are NOT research-derived doses."""


def choose_extra_opportunity(summary, config):
    if summary["data_status"] != "complete":
        return {
            "mode": "baseline_only",
            "observed_shortfall_minutes": summary["shortfall_minutes"],
            "recorded_days": summary["recorded_days"],
            "window_days": summary["window_days"],
            "data_status": summary["data_status"],
            "requested_extra_minutes_per_night": None,
            "additional_sleep_status": "deferred_insufficient_history",
            "needs_record_completion": True,
            "dose_basis": "not_estimated_incomplete_history",
            "rule_ids": ["PRODUCT_HISTORY_REQUIRED_FOR_EXTRA"],
        }
    if config.recovery_policy == "model":
        raise ValueError("model mode requires performance_model candidate search")
    shortfall = summary["shortfall_minutes"]
    selected_band = None
    extra = 0
    if shortfall > 0:
        if config.recovery_policy == "fixed":
            extra = config.extra_opportunity_minutes
        else:
            for index, (upper, minutes) in enumerate(config.adaptive_bands):
                if upper is None or shortfall <= upper:
                    selected_band = index
                    extra = minutes
                    break
    extra = min(extra, config.max_extra_opportunity_minutes)
    return {
        "mode": config.recovery_policy,
        "additional_sleep_status": "product_rule",
        "observed_shortfall_minutes": shortfall,
        "recorded_days": summary["recorded_days"],
        "window_days": summary["window_days"],
        "data_status": summary["data_status"],
        "selected_band_index": selected_band,
        "band_settings": [[upper, minutes] for upper, minutes in config.adaptive_bands],
        "requested_extra_minutes_per_night": extra,
        "max_extra_minutes_per_night": config.max_extra_opportunity_minutes,
        "needs_record_completion": summary["data_status"] != "complete",
        "dose_basis": "configurable_product_heuristic_not_validated_repayment_formula",
        "rule_ids": ["E_HISTORY", "E_NO_LINEAR_REPAYMENT", "PRODUCT_SHORTFALL_BANDS"],
    }
