#!/usr/bin/env python3
"""Measure JSON validity and intent accuracy on the Indonesian golden set."""

from __future__ import annotations

import argparse
import json
import re
from collections import Counter
from pathlib import Path

import torch
from peft import PeftModel
from transformers import AutoModelForCausalLM, AutoTokenizer

ROOT = Path(__file__).resolve().parent
METRICS = {"SPENDING", "INCOME", "BALANCE", "LARGEST", "COUNT", "BREAKDOWN", "UNSUPPORTED"}
PERIODS = {"TODAY", "THIS_WEEK", "LAST_WEEK", "THIS_MONTH", "LAST_MONTH", "THIS_YEAR", "LAST_7_DAYS", "ALL"}


def parse_json(text: str):
    text = re.sub(r"(?is)<think(?:ing)?>.*?</think>", "", text)
    start = text.find("{")
    end = text.rfind("}")
    if start < 0 or end <= start:
        return None
    try:
        value = json.loads(text[start : end + 1])
    except json.JSONDecodeError:
        return None
    return value if value.get("metric") in METRICS and value.get("period") in PERIODS else None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="Qwen/Qwen3.5-0.8B")
    parser.add_argument("--adapter", type=Path, default=None)
    parser.add_argument("--data", type=Path, default=ROOT / "data" / "golden_eval.jsonl")
    parser.add_argument("--limit", type=int, default=300)
    parser.add_argument("--output", type=Path, default=ROOT / "output" / "eval-report.json")
    args = parser.parse_args()

    tokenizer = AutoTokenizer.from_pretrained(args.base, trust_remote_code=True)
    model = AutoModelForCausalLM.from_pretrained(
        args.base,
        dtype=torch.bfloat16 if torch.cuda.is_available() and torch.cuda.is_bf16_supported() else torch.float32,
        device_map="auto" if torch.cuda.is_available() else None,
        trust_remote_code=True,
    )
    if args.adapter:
        model = PeftModel.from_pretrained(model, args.adapter)
    model.eval()

    rows = [json.loads(line) for line in args.data.read_text(encoding="utf-8").splitlines() if line.strip()][: args.limit]
    system_prompt = (ROOT / "system_prompt_id.txt").read_text(encoding="utf-8")
    system_prompt = system_prompt.replace(
        "{{CATEGORIES}}",
        "Makanan & Minuman, Transportasi, Belanja, Tagihan, Pulsa & Data, Hiburan, Kesehatan, Pendidikan, Donasi, Gaji, Transfer, Lainnya",
    )
    valid = 0
    correct = 0
    failures = []
    predicted_metrics = Counter()
    for row in rows:
        messages = [{"role": "system", "content": system_prompt}, {"role": "user", "content": row["question"]}]
        try:
            prompt = tokenizer.apply_chat_template(
                messages, tokenize=False, add_generation_prompt=True, enable_thinking=False
            )
        except TypeError:
            prompt = tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
        inputs = tokenizer(prompt, return_tensors="pt").to(model.device)
        with torch.no_grad():
            output = model.generate(
                **inputs,
                max_new_tokens=128,
                do_sample=False,
                temperature=None,
                top_p=None,
                top_k=None,
                pad_token_id=tokenizer.eos_token_id,
            )
        generated = tokenizer.decode(output[0][inputs["input_ids"].shape[-1] :], skip_special_tokens=True)
        predicted = parse_json(generated)
        if predicted:
            valid += 1
            predicted_metrics[predicted["metric"]] += 1
        expected = row["expected"]
        intent_ok = bool(predicted) and all(
            predicted.get(key) == expected.get(key) for key in ("metric", "period", "category")
        )
        if intent_ok:
            correct += 1
        elif len(failures) < 25:
            failures.append({"question": row["question"], "expected": expected, "raw": generated[:500]})

    report = {
        "examples": len(rows),
        "json_validity": valid / max(len(rows), 1),
        "intent_accuracy": correct / max(len(rows), 1),
        "predicted_metrics": dict(predicted_metrics),
        "failures": failures,
        "thresholds": {"json_validity": 0.90, "intent_accuracy": 0.80},
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({k: report[k] for k in ("examples", "json_validity", "intent_accuracy", "thresholds")}, indent=2))


if __name__ == "__main__":
    main()
