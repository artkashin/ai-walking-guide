#!/usr/bin/env python3
"""Idempotently create milestone issues via the configured gh identity, no credentials read."""
import json
from pathlib import Path
import subprocess

REPO = "artkashin/ai-walking-guide"
ROOT = Path(__file__).resolve().parents[1]


def gh(*args):
    return subprocess.check_output(["gh", *args], text=True).strip()


def main():
    specs = json.loads((ROOT / "planning/issues.json").read_text())
    existing = json.loads(gh("issue", "list", "--repo", REPO, "--state", "all", "--limit", "200", "--json", "title,url"))
    urls = {i["title"].split(" — ")[0]: i["url"] for i in existing}
    for spec in specs:
        key = spec["key"]
        if key in urls:
            print(f"{key}: already exists {urls[key]}")
            continue
        dependencies = ", ".join(urls[d] for d in spec["dependencies"]) or "None"
        body = f"Dependencies: {dependencies}\n\nAcceptance criteria:\n\n"
        body += "\n".join(f"- [ ] {criterion}" for criterion in spec["acceptance"])
        body += "\n\nAll work must comply with docs/CONSTITUTION.md. No new paid services or account permission grants without explicit approval.\n"
        # stdin preserves actual newlines, with no shell interpolation.
        result = subprocess.run(["gh", "issue", "create", "--repo", REPO, "--title", spec["title"], "--body-file", "-"], input=body, text=True, capture_output=True)
        if result.returncode:
            raise SystemExit(f"Cannot create {key}: {result.stderr.strip()}")
        urls[key] = result.stdout.strip()
        print(f"{key}: {urls[key]}")


if __name__ == "__main__":
    main()
