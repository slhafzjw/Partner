#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INDEX_PATH = ROOT / "registry" / "index.json"
MODULES_DIR = ROOT / "registry" / "modules"


def load_json(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def write_json(path: Path, data: dict) -> None:
    path.write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def build_external_modules() -> list[dict]:
    entries = []

    if not MODULES_DIR.exists():
        return entries

    for manifest_path in sorted(MODULES_DIR.glob("*.json")):
        manifest = load_json(manifest_path)

        # 这里按你当前 ModuleManifest 的完整文件结构取字段。
        # version 如果 manifest 里暂时没有，可以先默认取 "0.5.0" 或直接要求 manifest 必须有。
        name = manifest["name"]
        version = manifest["version"]
        with_gateway = manifest.get("withGateway", False)

        rel_path = manifest_path.relative_to(ROOT / "registry").as_posix()

        entries.append(
            {
                "name": name,
                "version": version,
                "withGateway": with_gateway,
                "registryRef": rel_path,
            }
        )

    return entries


def main() -> None:
    index = load_json(INDEX_PATH)
    index["externalModules"] = build_external_modules()
    write_json(INDEX_PATH, index)


if __name__ == "__main__":
    main()

