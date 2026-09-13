#!/usr/bin/env python3
"""Build repo index (index.min.json / index.json) + icons from built APKs.

Expects:
  - APK artifacts already moved into ./apk/
  - Inspector output at ./output.json
  - ANDROID_HOME pointing to an SDK with build-tools installed
"""

import json
import os
import re
import subprocess
from pathlib import Path
from zipfile import ZipFile

PACKAGE_NAME_REGEX = re.compile(r"package: name='([^']+)'")
VERSION_CODE_REGEX = re.compile(r"versionCode='([^']+)'")
VERSION_NAME_REGEX = re.compile(r"versionName='([^']+)'")
IS_NSFW_REGEX = re.compile(r"'tachiyomi.animeextension.nsfw' value='([^']+)'")
APPLICATION_LABEL_REGEX = re.compile(r"^application-label:'([^']+)'", re.MULTILINE)
APPLICATION_ICON_320_REGEX = re.compile(r"^application-icon-320:'([^']+)'", re.MULTILINE)
LANGUAGE_REGEX = re.compile(r"aniyomi-([^.]+)")

*_, ANDROID_BUILD_TOOLS = sorted((Path(os.environ["ANDROID_HOME"]) / "build-tools").iterdir())
REPO_DIR = Path(".")
REPO_APK_DIR = REPO_DIR / "apk"
REPO_ICON_DIR = REPO_DIR / "icon"

REPO_ICON_DIR.mkdir(parents=True, exist_ok=True)

with open("output.json", encoding="utf-8") as f:
    inspector_data = json.load(f)

index_data = []

for apk in sorted(REPO_APK_DIR.iterdir()):
    if apk.suffix != ".apk":
        continue

    badging = subprocess.check_output(
        [
            ANDROID_BUILD_TOOLS / "aapt",
            "dump",
            "--include-meta-data",
            "badging",
            apk,
        ]
    ).decode()

    package_info = next(x for x in badging.splitlines() if x.startswith("package: "))
    package_name = PACKAGE_NAME_REGEX.search(package_info)[1]
    application_icon = APPLICATION_ICON_320_REGEX.search(badging)[1]

    with ZipFile(apk) as z, z.open(application_icon) as i, (
        REPO_ICON_DIR / f"{package_name}.png"
    ).open("wb") as f:
        f.write(i.read())

    language = LANGUAGE_REGEX.search(apk.name)[1]
    sources = inspector_data.get(package_name, [])

    if len(sources) == 1:
        source_language = sources[0]["lang"]
        if (
            source_language != language
            and source_language not in {"all", "other"}
            and language not in {"all", "other"}
        ):
            language = source_language

    entry = {
        "name": APPLICATION_LABEL_REGEX.search(badging)[1],
        "pkg": package_name,
        "apk": apk.name,
        "lang": language,
        "code": int(VERSION_CODE_REGEX.search(package_info)[1]),
        "version": VERSION_NAME_REGEX.search(package_info)[1],
        "nsfw": int(IS_NSFW_REGEX.search(badging)[1]),
        "sources": [
            {
                "name": source["name"],
                "lang": source["lang"],
                "id": source["id"],
                "baseUrl": source["baseUrl"],
                "versionId": source["versionId"],
            }
            for source in sources
        ],
    }

    index_data.append(entry)

index_data.sort(key=lambda x: x["name"])

with REPO_DIR.joinpath("index.min.json").open("w", encoding="utf-8") as f:
    json.dump(index_data, f, ensure_ascii=False, separators=(",", ":"))

with REPO_DIR.joinpath("index.json").open("w", encoding="utf-8") as f:
    json.dump(index_data, f, ensure_ascii=False, indent=2)
    f.write("\n")

print(f"Indexed {len(index_data)} extension(s):")
for entry in index_data:
    print(f"  {entry['name']} {entry['version']} ({entry['pkg']}) sources={len(entry['sources'])}")
