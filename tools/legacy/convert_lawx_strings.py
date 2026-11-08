#!/usr/bin/env python3
"""One-time migration of lawXgram strings (strings_lawx.xml, strings_accessibility.xml) into
fork/res/values*/strings_neo.xml.

* every key gets the `Neo` prefix (Telegram's server language packs win over local strings for
  unprefixed keys, see AGENTS.md);
* the app name is never a literal: every localized form of the old brand ("lawXgram",
  "локсграм", inflected forms like "lawXgramni") becomes a format argument `%N$s`, filled with
  NeoBrand.appName() in code, so translations can never localize the name again;
* strings of removed features are dropped.

Writes tools/legacy/strings-keymap.tsv (old key -> new key, brand argument index) so ported
features can find their strings. Usage: python tools/legacy/convert_lawx_strings.py [<git rev>]
"""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REV = sys.argv[1] if len(sys.argv) > 1 else "c069ede4"
RES = "TMessagesProj/src/main/res"
SOURCES = ("strings_lawx.xml", "strings_accessibility.xml")

# Strings of features that are not carried over (see the plan: helper-bot layer, cloud sync,
# registration date, DC ping, Nekogram leftovers, "blame Durov" stubs, content-restriction bypass).
DROP_EXACT = {
    "AppNameLawx", "lawXgram", "lawXgramBeta", "OfficialChannelUsername", "Nya", "Cryptocurrency",
    "DonateEvilGoogle", "GooglePlay", "DidNotGetTheCodeBlameDurov", "ChangePhoneNumberBlameDurov",
    "CreatePasskeyBlameDurov", "IgnoreContentRestriction", "WsEnableTls", "WsProvider",
    "EventType", "DependsOnDate", "Christmas", "Valentine", "Halloween", "DatacenterStatus",
    "DatacenterStatusAbout", "DatacenterStatusShort", "SoonRemovedOption",
}
DROP_PREFIXES = ("CloudConfig", "RegistrationDate")

RENAMES = {
    "LawxSettings": "NeoSettings",
    "lawXgramRunning": "NeoRunningNotification",
    "UpdatelawXgram": "NeoUpdateApp",
    "InviteTextLawx": "NeoInviteText",
    "SharelawXgram": "NeoShareApp",
    "lawXgramVersion": "NeoVersionInfo",
    "TranslatorTypeLawx": "NeoTranslatorTypeInMessage",
    "UnknownLawxSettingsOption": "NeoUnknownSettingsOption",
    "PasscodeLawx": "NeoPasscode",
}

BRAND_RE = re.compile(r"(?:lawxgram|локсграм)", re.IGNORECASE)
STRING_RE = re.compile(r'<string\s+name="([^"]+)"([^>]*)>(.*?)</string>', re.S)
POS_ARG_RE = re.compile(r"%(\d+)\$")
PLAIN_ARG_RE = re.compile(r"%(?!\d+\$)(?!%)[-#+ 0,(]*\d*(?:\.\d+)?[sdfxXc]")


def git_show(path: str) -> str | None:
    proc = subprocess.run(["git", "show", f"{REV}:{path}"], cwd=ROOT, capture_output=True)
    return proc.stdout.decode("utf-8") if proc.returncode == 0 else None


def list_files() -> list[str]:
    out = subprocess.run(["git", "ls-tree", "-r", "--name-only", REV, RES], cwd=ROOT,
                         capture_output=True, text=True, check=True).stdout.split()
    return sorted(f for f in out if f.rsplit("/", 1)[-1] in SOURCES)


def parse(text: str) -> dict[str, tuple[str, str]]:
    return {name: (attrs, value) for name, attrs, value in STRING_RE.findall(text)}


def new_key(old: str) -> str:
    if old in RENAMES:
        return RENAMES[old]
    return "Neo" + old[0].upper() + old[1:]


def dropped(old: str) -> bool:
    return old in DROP_EXACT or old.startswith(DROP_PREFIXES)


def main() -> int:
    files = list_files()
    by_dir: dict[str, dict[str, tuple[str, str]]] = {}
    for f in files:
        d = f.split("/")[-2]
        text = git_show(f)
        if text is None:
            continue
        by_dir.setdefault(d, {}).update(parse(text))
    default = by_dir.pop("values")

    # A string takes the app name as an argument when its source or any translation names the app
    # (some translators added the name where the English text has none: the extra argument is then
    # simply unused by String.format in English).
    brand_arg: dict[str, int] = {}
    for old, (_, value) in default.items():
        if dropped(old):
            continue
        mentioned = BRAND_RE.search(value) or any(
            BRAND_RE.search(entries[old][1]) for entries in by_dir.values() if old in entries)
        if not mentioned:
            continue
        if PLAIN_ARG_RE.search(value):
            raise SystemExit(f"{old}: mixes the brand with non-positional format arguments: {value}")
        indices = [int(i) for i in POS_ARG_RE.findall(value)]
        brand_arg[old] = (max(indices) if indices else 0) + 1

    def convert(old: str, value: str) -> str:
        if old in brand_arg:
            value = BRAND_RE.sub(f"%{brand_arg[old]}$s", value)
        return value

    def render(entries: dict[str, tuple[str, str]], comment: str) -> str:
        lines = ['<?xml version="1.0" encoding="utf-8"?>', comment, "<resources>"]
        for old, (attrs, value) in entries.items():
            if dropped(old):
                continue
            attrs = re.sub(r'\s*translatable="[^"]*"', "", attrs)
            value = convert(old, value)
            if "@string/" in value:
                print(f"warning: {old} still references another string: {value}", file=sys.stderr)
                continue
            lines.append(f'    <string name="{new_key(old)}"{attrs}>{value}</string>')
        lines.append("</resources>")
        return "\n".join(lines) + "\n"

    out_root = ROOT / "fork" / "res"
    header = ("<!-- Neogram strings. Keys start with Neo; the app name is passed as an argument, never "
              "written. Translations: Crowdin. -->")
    (out_root / "values").mkdir(parents=True, exist_ok=True)
    (out_root / "values" / "strings_neo.xml").write_text(render(default, header), encoding="utf-8")
    count = 0
    for d, entries in sorted(by_dir.items()):
        kept = {k: v for k, v in entries.items() if k in default}
        if not kept:
            continue
        (out_root / d).mkdir(parents=True, exist_ok=True)
        (out_root / d / "strings_neo.xml").write_text(
            render(kept, "<!-- Translated on Crowdin; do not edit by hand. -->"), encoding="utf-8")
        count += 1

    keymap = ["# old lawXgram key\tnew Neogram key\tbrand argument index (0 = none)"]
    for old in default:
        if not dropped(old):
            keymap.append(f"{old}\t{new_key(old)}\t{brand_arg.get(old, 0)}")
    (ROOT / "tools" / "legacy" / "strings-keymap.tsv").write_text("\n".join(keymap) + "\n", encoding="utf-8")
    kept_count = sum(1 for k in default if not dropped(k))
    print(f"default: {kept_count} keys kept, {len(default) - kept_count} dropped; {count} translations; "
          f"{len(brand_arg)} strings take the app name as an argument")
    return 0


if __name__ == "__main__":
    sys.exit(main())
