#!/usr/bin/env python3
"""Merge a trained Catet.in LoRA adapter into a standalone HF checkpoint."""

import argparse
from pathlib import Path

import torch
from peft import AutoPeftModelForCausalLM
from transformers import AutoTokenizer


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--adapter", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--base", default="Qwen/Qwen3.5-0.8B")
    args = parser.parse_args()

    model = AutoPeftModelForCausalLM.from_pretrained(
        args.adapter,
        torch_dtype=torch.bfloat16,
        device_map="cpu",
        trust_remote_code=True,
    )
    merged = model.merge_and_unload()
    merged.save_pretrained(args.output, safe_serialization=True)
    AutoTokenizer.from_pretrained(args.base, trust_remote_code=True).save_pretrained(args.output)
    print(f"Model gabungan tersimpan di {args.output}")


if __name__ == "__main__":
    main()
