#!/usr/bin/env python3
"""QLoRA fine-tuning for Catet.in intent JSON extraction.

This script is intentionally generic around the official Qwen chat template.
Use a CUDA GPU with at least 8 GB VRAM (12 GB recommended). A CPU-only iGPU
is not a practical training target.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import torch
from datasets import load_dataset
from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig
from trl import SFTConfig, SFTTrainer

ROOT = Path(__file__).resolve().parent


def apply_chat_template(tokenizer, messages):
    kwargs = dict(tokenize=False, add_generation_prompt=False)
    try:
        return tokenizer.apply_chat_template(messages, enable_thinking=False, **kwargs)
    except TypeError:
        return tokenizer.apply_chat_template(messages, **kwargs)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default="Qwen/Qwen3.5-0.8B")
    parser.add_argument("--train", type=Path, default=ROOT / "data" / "train.jsonl")
    parser.add_argument("--eval", type=Path, default=ROOT / "data" / "eval.jsonl")
    parser.add_argument("--output", type=Path, default=ROOT / "output" / "catetin-qlora")
    parser.add_argument("--epochs", type=float, default=3.0)
    parser.add_argument("--learning-rate", type=float, default=2e-4)
    parser.add_argument("--max-length", type=int, default=1536)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    if not torch.cuda.is_available():
        raise SystemExit(
            "QLoRA memerlukan GPU CUDA. Mesin CPU/iGPU ini tidak cocok untuk melatih model. "
            "Jalankan skrip di GPU lokal atau layanan cloud sesuai README."
        )

    tokenizer = AutoTokenizer.from_pretrained(args.model, trust_remote_code=True)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token

    quant_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_use_double_quant=True,
        bnb_4bit_compute_dtype=torch.bfloat16 if torch.cuda.is_bf16_supported() else torch.float16,
    )
    model = AutoModelForCausalLM.from_pretrained(
        args.model,
        quantization_config=quant_config,
        device_map="auto",
        trust_remote_code=True,
    )
    model = prepare_model_for_kbit_training(model)
    model.config.use_cache = False

    lora = LoraConfig(
        r=16,
        lora_alpha=32,
        lora_dropout=0.05,
        bias="none",
        task_type="CAUSAL_LM",
        target_modules="all-linear",
    )
    model = get_peft_model(model, lora)
    model.print_trainable_parameters()

    train_raw = load_dataset("json", data_files=str(args.train), split="train")
    eval_raw = load_dataset("json", data_files=str(args.eval), split="train")

    def render(example):
        return {"text": apply_chat_template(tokenizer, example["messages"])}

    train_data = train_raw.map(render, remove_columns=train_raw.column_names)
    eval_data = eval_raw.map(render, remove_columns=eval_raw.column_names)

    args.output.mkdir(parents=True, exist_ok=True)
    training_args = SFTConfig(
        output_dir=str(args.output),
        num_train_epochs=args.epochs,
        per_device_train_batch_size=1,
        per_device_eval_batch_size=1,
        gradient_accumulation_steps=16,
        learning_rate=args.learning_rate,
        lr_scheduler_type="cosine",
        warmup_ratio=0.03,
        logging_steps=10,
        save_strategy="epoch",
        eval_strategy="epoch",
        save_total_limit=2,
        bf16=torch.cuda.is_bf16_supported(),
        fp16=not torch.cuda.is_bf16_supported(),
        gradient_checkpointing=True,
        max_length=args.max_length,
        dataset_text_field="text",
        packing=False,
        optim="paged_adamw_8bit",
        report_to="none",
        seed=args.seed,
    )
    trainer = SFTTrainer(
        model=model,
        processing_class=tokenizer,
        train_dataset=train_data,
        eval_dataset=eval_data,
        args=training_args,
    )
    trainer.train()
    trainer.save_model(str(args.output))
    tokenizer.save_pretrained(args.output)
    print(f"Adapter tersimpan di {args.output}")


if __name__ == "__main__":
    main()
