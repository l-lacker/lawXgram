#!/usr/bin/env python3
"""Neogram patchset tool.

The repository stores Neogram as a series of small patches on top of a pinned
upstream Telegram Android commit.  This tool materializes the series into a
git worktree (one commit per patch), exports commits back into deterministic
patch files, and helps with editing the stack and rebasing it onto a newer
upstream.

Only the Python 3.9+ standard library and git >= 2.35 are required.  Run
`patchset.cmd <command>` on Windows or `./patchset.sh <command>` elsewhere.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

GROUPS = ("misc", "hooks", "feature", "debloat", "bugfix")
NAME_RE = re.compile(r"^(misc|hooks|feature|debloat|bugfix)/([a-z0-9][a-z0-9-]*)$")
SUBJECT_RE = re.compile(r"^(misc|hooks|feature|debloat|bugfix)/([a-z0-9][a-z0-9-]*): ([ -~]+)$")
INDEX_RE = re.compile(rb"^index [0-9a-f]+\.\.[0-9a-f]+( [0-7]{6})?$")
DIFF_GIT_RE = re.compile(rb"^diff --git a/(.*) b/(.*)$")
HUNK_RE = re.compile(rb"^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@")

BASE_TAG = "patchset/base"
STACK_BRANCH = "stack"
IDENT_NAME = "Neogram Patchset"
IDENT_EMAIL = "patches@neogram.invalid"
FIXED_DATE = "946684800 +0000"  # 2000-01-01T00:00:00Z
ZERO_FROM = b"From 0000000000000000000000000000000000000000 Mon Sep 17 00:00:00 2001"
STATE_FILE = "patchset-state.json"

HOOK_BUDGET = {"hooks": 40, "bugfix": 80}


class PatchsetError(Exception):
    """A user-facing error: printed without a traceback."""


# --------------------------------------------------------------------------------------
# Paths and properties
# --------------------------------------------------------------------------------------

@dataclass
class Ctx:
    root: Path
    worktree: Path
    verbose: bool = False

    @property
    def props_path(self) -> Path:
        return self.root / "patchset.properties"

    @property
    def series_path(self) -> Path:
        return self.root / "series"

    @property
    def patches_dir(self) -> Path:
        return self.root / "patches"

    @property
    def git_dir(self) -> Path:
        return self.worktree / ".git"


def read_properties(path: Path) -> Dict[str, str]:
    result: Dict[str, str] = {}
    if not path.exists():
        return result
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or line.startswith("!"):
            continue
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        result[key.strip()] = value.strip()
    return result


def write_properties(path: Path, updates: Dict[str, str]) -> None:
    """Update keys in place, keeping comments and order; append new keys."""
    lines = path.read_text(encoding="utf-8").splitlines() if path.exists() else []
    seen = set()
    out = []
    for raw in lines:
        stripped = raw.strip()
        if stripped and not stripped.startswith(("#", "!")) and "=" in stripped:
            key = stripped.split("=", 1)[0].strip()
            if key in updates:
                out.append(f"{key}={updates[key]}")
                seen.add(key)
                continue
        out.append(raw)
    for key, value in updates.items():
        if key not in seen:
            out.append(f"{key}={value}")
    write_text_lf(path, "\n".join(out) + "\n")


def write_text_lf(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)


def write_bytes_if_changed(path: Path, data: bytes) -> bool:
    if path.exists() and path.read_bytes() == data:
        return False
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(data)
    return True


def read_series(ctx: Ctx) -> List[str]:
    if not ctx.series_path.exists():
        return []
    names = []
    for raw in ctx.series_path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if not line.endswith(".patch"):
            raise PatchsetError(f"series: entry must end with .patch: {line}")
        name = line[: -len(".patch")]
        if not NAME_RE.match(name):
            raise PatchsetError(f"series: bad patch name '{name}' (expected <group>/<kebab-name>)")
        names.append(name)
    if len(set(names)) != len(names):
        raise PatchsetError("series: duplicate entries")
    return names


def patch_path(ctx: Ctx, name: str) -> Path:
    return ctx.patches_dir / (name + ".patch")


def upstream_props(ctx: Ctx) -> Dict[str, str]:
    props = read_properties(ctx.props_path)
    for key in ("upstream.url", "upstream.commit"):
        if not props.get(key):
            raise PatchsetError(f"{ctx.props_path.name}: missing {key}")
    if not re.fullmatch(r"[0-9a-f]{40}", props["upstream.commit"]):
        raise PatchsetError("upstream.commit must be a full 40-character sha")
    return props


# --------------------------------------------------------------------------------------
# Process helpers
# --------------------------------------------------------------------------------------

def base_env() -> Dict[str, str]:
    env = os.environ.copy()
    env["GIT_ASK_YESNO"] = "false"
    env["GIT_TERMINAL_PROMPT"] = "0"
    env.pop("GIT_DIR", None)
    env.pop("GIT_WORK_TREE", None)
    env.pop("GIT_INDEX_FILE", None)
    return env


def run(args: Sequence[str], cwd: Optional[Path] = None, env: Optional[Dict[str, str]] = None,
        input: Optional[bytes] = None, check: bool = True, capture: bool = True) -> subprocess.CompletedProcess:
    full_env = base_env()
    if env:
        full_env.update(env)
    proc = subprocess.run(
        list(args), cwd=str(cwd) if cwd else None, env=full_env, input=input,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
    )
    if check and proc.returncode != 0:
        err = proc.stderr.decode("utf-8", "replace") if proc.stderr else ""
        out = proc.stdout.decode("utf-8", "replace") if proc.stdout else ""
        raise PatchsetError(f"command failed ({proc.returncode}): {' '.join(args)}\n{out}{err}".rstrip())
    return proc


def git(cwd: Path, *args: str, env: Optional[Dict[str, str]] = None, input: Optional[bytes] = None,
        check: bool = True) -> str:
    proc = run(["git", *args], cwd=cwd, env=env, input=input, check=check)
    return proc.stdout.decode("utf-8", "replace").rstrip("\n") if proc.stdout else ""


def git_bytes(cwd: Path, *args: str, env: Optional[Dict[str, str]] = None, input: Optional[bytes] = None) -> bytes:
    return run(["git", *args], cwd=cwd, env=env, input=input).stdout


def git_ok(cwd: Path, *args: str) -> bool:
    return run(["git", *args], cwd=cwd, check=False).returncode == 0


def git_passthrough(cwd: Path, *args: str, env: Optional[Dict[str, str]] = None) -> int:
    """Run git with inherited stdio so the user sees conflicts/progress."""
    full_env = base_env()
    if env:
        full_env.update(env)
    return subprocess.run(["git", *args], cwd=str(cwd), env=full_env).returncode


def fixed_identity_env() -> Dict[str, str]:
    return {
        "GIT_AUTHOR_NAME": IDENT_NAME,
        "GIT_AUTHOR_EMAIL": IDENT_EMAIL,
        "GIT_AUTHOR_DATE": FIXED_DATE,
        "GIT_COMMITTER_NAME": IDENT_NAME,
        "GIT_COMMITTER_EMAIL": IDENT_EMAIL,
        "GIT_COMMITTER_DATE": FIXED_DATE,
    }


def isolated_config_env(ctx_or_dir: Path) -> Dict[str, str]:
    """Ignore user/system git config so exports do not depend on the machine."""
    empty = ctx_or_dir / "patchset-empty.gitconfig"
    if not empty.exists():
        empty.write_bytes(b"")
    return {"GIT_CONFIG_GLOBAL": str(empty), "GIT_CONFIG_NOSYSTEM": "1"}


def info(msg: str) -> None:
    print(msg, flush=True)


def warn(msg: str) -> None:
    print(f"warning: {msg}", file=sys.stderr, flush=True)


def posix_path(p: str) -> str:
    return p.replace("\\", "/")


# --------------------------------------------------------------------------------------
# Patch format
# --------------------------------------------------------------------------------------

@dataclass
class ParsedPatch:
    subject: str
    body: str
    diff: bytes  # starts at the first "diff --git" line

    @property
    def name(self) -> str:
        m = SUBJECT_RE.match(self.subject)
        if not m:
            raise PatchsetError(f"bad patch subject: {self.subject!r}")
        return f"{m.group(1)}/{m.group(2)}"

    def message(self) -> bytes:
        msg = self.subject
        if self.body:
            msg += "\n\n" + self.body
        return (msg + "\n").encode("utf-8")


def parse_patch(data: bytes, origin: str = "patch") -> ParsedPatch:
    lines = data.split(b"\n")
    i = 0
    subject = None
    while i < len(lines) and lines[i] != b"":
        line = lines[i]
        if line.startswith(b"Subject: "):
            subject = line[len(b"Subject: "):]
            while i + 1 < len(lines) and lines[i + 1][:1] in (b" ", b"\t"):
                i += 1
                subject += lines[i]
        i += 1
    if subject is None:
        raise PatchsetError(f"{origin}: no Subject header")
    i += 1  # skip the blank separator line
    body_lines = []
    while i < len(lines) and lines[i] != b"---":
        if lines[i].startswith(b"diff --git "):
            break
        body_lines.append(lines[i])
        i += 1
    while body_lines and body_lines[-1].strip() == b"":
        body_lines.pop()
    diff_start = None
    for j in range(i, len(lines)):
        if lines[j].startswith(b"diff --git "):
            diff_start = j
            break
    if diff_start is None:
        raise PatchsetError(f"{origin}: no diff")
    diff = b"\n".join(lines[diff_start:])
    return ParsedPatch(subject.decode("utf-8"), b"\n".join(body_lines).decode("utf-8"), diff)


def normalize_format_patch(raw: bytes) -> bytes:
    lines = raw.split(b"\n")
    out: List[bytes] = []
    i = 0
    in_header = True
    while i < len(lines):
        line = lines[i]
        if in_header:
            if i == 0 and line.startswith(b"From "):
                out.append(ZERO_FROM)
            elif line == b"":
                in_header = False
                out.append(line)
            elif line.startswith(b"From: "):
                out.append(f"From: {IDENT_NAME} <{IDENT_EMAIL}>".encode())
            elif line.startswith(b"Date: "):
                pass
            elif line.startswith(b"Subject: "):
                subject = line
                while i + 1 < len(lines) and lines[i + 1][:1] in (b" ", b"\t"):
                    i += 1
                    subject += lines[i]
                out.append(subject)
            else:
                out.append(line)
            i += 1
            continue
        m = INDEX_RE.match(line)
        if m:
            out.append(b"index 0000000..0000000" + (m.group(1) or b""))
        else:
            out.append(line)
        i += 1
    return b"\n".join(out)


FORMAT_PATCH_ARGS = [
    "-c", "core.quotePath=false",
    "-c", "diff.renames=false",
    "-c", "diff.algorithm=myers",
    "-c", "diff.indentHeuristic=true",
    "-c", "i18n.logOutputEncoding=UTF-8",
    "format-patch", "-1", "--stdout", "--zero-commit", "--no-signature", "--keep-subject",
    "--no-encode-email-headers", "--no-renames", "--no-color", "--no-numbered",
    "-U3", "--stat=100,80",
]


def export_commit(ctx: Ctx, sha: str) -> bytes:
    raw = git_bytes(ctx.worktree, *FORMAT_PATCH_ARGS, sha, env=isolated_config_env(ctx.git_dir))
    if b"\nGIT binary patch\n" in raw:
        raise PatchsetError(f"commit {sha[:10]} contains a binary diff; put binaries into fork/ instead")
    return normalize_format_patch(raw)


def subject_of(ctx: Ctx, sha: str) -> str:
    return git(ctx.worktree, "log", "-1", "--format=%s", sha)


def name_of_subject(subject: str) -> str:
    if subject.startswith(("fixup! ", "squash! ", "amend! ")):
        raise PatchsetError(f"unsquashed commit '{subject}': run `patchset fixup` or squash it first")
    m = SUBJECT_RE.match(subject)
    if not m:
        raise PatchsetError(
            f"commit subject '{subject}' must look like '<group>/<kebab-name>: <ascii description>' "
            f"with group in {', '.join(GROUPS)}")
    return f"{m.group(1)}/{m.group(2)}"


# --------------------------------------------------------------------------------------
# Worktree helpers
# --------------------------------------------------------------------------------------

def require_worktree(ctx: Ctx) -> None:
    if not (ctx.worktree / ".git").exists():
        raise PatchsetError(f"no worktree at {ctx.worktree}; run `patchset setup` first")


def stack_commits(ctx: Ctx) -> List[str]:
    out = git(ctx.worktree, "rev-list", "--reverse", "--first-parent", f"{BASE_TAG}..HEAD")
    return [line for line in out.splitlines() if line]


def stack_entries(ctx: Ctx) -> List[Tuple[str, str]]:
    return [(sha, name_of_subject(subject_of(ctx, sha))) for sha in stack_commits(ctx)]


def find_commit(ctx: Ctx, name: str) -> str:
    if name.endswith(".patch"):
        name = name[:-6]
    if name.startswith("patches/"):
        name = name[len("patches/"):]
    for sha, n in stack_entries(ctx):
        if n == name:
            return sha
    raise PatchsetError(f"patch '{name}' is not in the worktree stack")


def is_dirty(ctx: Ctx) -> bool:
    out = git(ctx.worktree, "status", "--porcelain", "--untracked-files=no", "--ignore-submodules=dirty")
    return bool(out.strip())


def rebase_in_progress(cwd: Path) -> bool:
    gd = Path(git(cwd, "rev-parse", "--git-dir"))
    if not gd.is_absolute():
        gd = cwd / gd
    return (gd / "rebase-merge").exists() or (gd / "rebase-apply").exists()


def apply_patch_commit(cwd: Path, data: bytes, origin: str) -> str:
    parsed = parse_patch(data, origin)
    diff = parsed.diff if parsed.diff.endswith(b"\n") else parsed.diff + b"\n"
    proc = run(["git", "apply", "--index", "--whitespace=nowarn", "-"], cwd=cwd, input=diff, check=False)
    if proc.returncode != 0:
        raise PatchsetError(f"{origin} does not apply:\n{proc.stderr.decode('utf-8', 'replace').strip()}")
    run(["git", "commit", "-q", "--no-verify", "--allow-empty", "--cleanup=verbatim", "-F", "-"],
        cwd=cwd, input=parsed.message(), env=fixed_identity_env())
    return git(cwd, "rev-parse", "HEAD")


def stop_gradle(ctx: Ctx, enabled: bool) -> None:
    if not enabled or os.name != "nt":
        return
    wrapper = ctx.worktree / "gradlew.bat"
    if wrapper.exists():
        info("stopping Gradle daemons (Windows keeps files locked otherwise)…")
        subprocess.run([str(wrapper), "--stop"], cwd=str(ctx.worktree), stdout=subprocess.DEVNULL,
                       stderr=subprocess.DEVNULL)


def load_state(ctx: Ctx) -> Dict[str, str]:
    path = ctx.git_dir / STATE_FILE
    if path.exists():
        return json.loads(path.read_text(encoding="utf-8"))
    return {}


def save_state(ctx: Ctx, state: Optional[Dict[str, str]]) -> None:
    path = ctx.git_dir / STATE_FILE
    if state is None:
        if path.exists():
            path.unlink()
        return
    write_text_lf(path, json.dumps(state, indent=2))


def configure_worktree_repo(cwd: Path, ci: bool) -> None:
    settings = [
        ("core.autocrlf", "false"),
        ("core.eol", "lf"),
        ("core.longpaths", "true"),
        ("core.safecrlf", "false"),
        ("gc.auto", "0"),
        ("diff.ignoreSubmodules", "dirty"),
        ("rebase.instructionFormat", "%s"),
        ("rebase.abbreviateCommands", "false"),
        ("advice.detachedHead", "false"),
    ]
    if not ci:
        settings.append(("rerere.enabled", "true"))
    if os.name == "nt":
        settings.append(("core.fileMode", "false"))
    for key, value in settings:
        git(cwd, "config", key, value)
    if not git(cwd, "config", "user.name", check=False):
        git(cwd, "config", "user.name", "Neogram Developer")
    if not git(cwd, "config", "user.email", check=False):
        git(cwd, "config", "user.email", "dev@neogram.invalid")


def write_worktree_helpers(ctx: Ctx) -> None:
    exclude = ctx.git_dir / "info" / "exclude"
    wanted = ["/neo-build.properties", "/local.properties", "/.idea/", "/TMessagesProj_App/src/*/google-services.json"]
    existing = exclude.read_text(encoding="utf-8").splitlines() if exclude.exists() else []
    missing = [w for w in wanted if w not in existing]
    if missing:
        write_text_lf(exclude, "\n".join(existing + missing) + "\n")

    rel_root = os.path.relpath(ctx.root, ctx.worktree)
    write_text_lf(ctx.worktree / "neo-build.properties",
                  "# Generated by tools/patchset.py setup. Path to the patchset repository root.\n"
                  f"neo.root={posix_path(rel_root)}\n")

    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    local_props = ctx.worktree / "local.properties"
    if sdk and not local_props.exists():
        escaped = sdk.replace("\\", "\\\\").replace(":", "\\:")
        write_text_lf(local_props, f"sdk.dir={escaped}\n")

    tracked_idea = git(ctx.worktree, "ls-files", ".idea", check=False)
    vcs = ctx.worktree / ".idea" / "vcs.xml"
    if not tracked_idea and not vcs.exists():
        rel = posix_path(rel_root)
        write_text_lf(vcs, (
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<project version="4">\n'
            '  <component name="VcsDirectoryMappings">\n'
            '    <mapping directory="$PROJECT_DIR$" vcs="Git" />\n'
            f'    <mapping directory="$PROJECT_DIR$/{rel}" vcs="Git" />\n'
            '  </component>\n'
            '</project>\n'))


def update_submodules(ctx: Ctx, ci: bool, skip: bool) -> None:
    if skip or not (ctx.worktree / ".gitmodules").exists():
        return
    info("updating submodules…")
    args = ["-c", "core.longpaths=true", "-c", "protocol.file.allow=always", "submodule", "update", "--init",
            "--recursive", "--jobs", "8", "--depth", "1"]
    proc = run(["git", *args], cwd=ctx.worktree, check=False, capture=False)
    if proc.returncode != 0:
        warn("shallow submodule update failed, retrying with full history for the failing ones")
        args.remove("--depth")
        args.remove("1")
        run(["git", *args], cwd=ctx.worktree, capture=False)


def fetch_upstream(cwd: Path, url: str, ref: str, depth: bool = True, blobless: bool = False) -> str:
    if not git_ok(cwd, "remote", "get-url", "upstream"):
        git(cwd, "remote", "add", "upstream", url)
    else:
        git(cwd, "remote", "set-url", "upstream", url)
    args = ["fetch", "--no-tags", "-q"]
    if depth:
        args += ["--depth=1"]
    if blobless:
        args += ["--filter=blob:none"]
    args += ["upstream", ref]
    proc = run(["git", *args], cwd=cwd, check=False)
    if proc.returncode != 0:
        raise PatchsetError(f"cannot fetch {ref} from {url}:\n{proc.stderr.decode('utf-8', 'replace').strip()}")
    return git(cwd, "rev-parse", "FETCH_HEAD^{commit}")


# --------------------------------------------------------------------------------------
# Sync logic shared by setup/sync
# --------------------------------------------------------------------------------------

def first_divergence(ctx: Ctx, series: List[str]) -> Tuple[int, List[Tuple[str, str]]]:
    entries = stack_entries(ctx)
    for idx, (sha, name) in enumerate(entries):
        if idx >= len(series) or series[idx] != name:
            return idx, entries
        path = patch_path(ctx, name)
        if not path.exists() or export_commit(ctx, sha) != path.read_bytes():
            return idx, entries
    return len(entries), entries


def apply_series_from(ctx: Ctx, series: List[str], start: int) -> None:
    for name in series[start:]:
        path = patch_path(ctx, name)
        if not path.exists():
            raise PatchsetError(f"series lists {name} but {path} is missing")
        apply_patch_commit(ctx.worktree, path.read_bytes(), str(path.relative_to(ctx.root)))
        if ctx.verbose:
            info(f"  applied {name}")


def sync_worktree(ctx: Ctx, force: bool) -> None:
    series = read_series(ctx)
    if rebase_in_progress(ctx.worktree):
        raise PatchsetError("a rebase is in progress in the worktree; finish it with `patchset continue` or `abort`")
    if is_dirty(ctx):
        raise PatchsetError("worktree has uncommitted changes; commit them (new/fixup) or stash them first")
    idx, entries = first_divergence(ctx, series)
    if idx == len(entries) == len(series):
        info(f"worktree is up to date ({len(series)} patches)")
        return
    if idx < len(entries):
        dropped = [name for _, name in entries[idx:]]
        if not force:
            raise PatchsetError(
                "worktree commits differ from patches/ starting at "
                f"'{entries[idx][1]}'. Commits that would be replaced:\n  " + "\n  ".join(dropped) +
                "\nIf these are unexported local edits, run `patchset export` first. "
                "Otherwise rerun with --force to rebuild them from patches/.")
        target = entries[idx - 1][0] if idx > 0 else BASE_TAG
        git(ctx.worktree, "reset", "-q", "--hard", target)
    apply_series_from(ctx, series, idx)
    info(f"applied {len(series) - idx} patch(es); stack has {len(series)} patches")


# --------------------------------------------------------------------------------------
# Commands
# --------------------------------------------------------------------------------------

def cmd_setup(ctx: Ctx, args: argparse.Namespace) -> int:
    props = upstream_props(ctx)
    url, commit = props["upstream.url"], props["upstream.commit"]
    if (ctx.worktree / ".git").exists():
        base = git(ctx.worktree, "rev-parse", f"{BASE_TAG}^{{commit}}", check=False)
        if base != commit:
            if not args.force:
                raise PatchsetError(
                    f"worktree is based on {base[:12] or '?'} but patchset.properties pins {commit[:12]}.\n"
                    "Run `patchset rebase <commit>` to move the stack, or `patchset setup --force` to rebuild it.")
            info("re-basing the worktree on the pinned commit (--force)…")
            fetch_upstream(ctx.worktree, url, commit)
            git(ctx.worktree, "checkout", "-q", "-f", "-B", STACK_BRANCH, commit)
            git(ctx.worktree, "tag", "-f", BASE_TAG, commit)
        configure_worktree_repo(ctx.worktree, args.ci)
        sync_worktree(ctx, args.force)
    else:
        info(f"creating worktree at {ctx.worktree}")
        ctx.worktree.mkdir(parents=True, exist_ok=True)
        git(ctx.worktree, "init", "-q")
        configure_worktree_repo(ctx.worktree, args.ci)
        info(f"fetching {url} @ {commit[:12]}…")
        fetch_upstream(ctx.worktree, url, commit)
        git(ctx.worktree, "checkout", "-q", "-B", STACK_BRANCH, commit)
        git(ctx.worktree, "tag", "-f", BASE_TAG, commit)
        series = read_series(ctx)
        apply_series_from(ctx, series, 0)
        info(f"applied {len(series)} patch(es)")
    update_submodules(ctx, args.ci, args.no_submodules)
    write_worktree_helpers(ctx)
    info("done. Open the worktree directory in Android Studio.")
    return 0


def cmd_sync(ctx: Ctx, args: argparse.Namespace) -> int:
    require_worktree(ctx)
    sync_worktree(ctx, args.force)
    write_worktree_helpers(ctx)
    return 0


def build_exports(ctx: Ctx) -> List[Tuple[str, bytes]]:
    result = []
    names = set()
    for sha, name in stack_entries(ctx):
        if name in names:
            raise PatchsetError(f"two commits in the stack are named {name}")
        names.add(name)
        result.append((name, export_commit(ctx, sha)))
    return result


def cmd_export(ctx: Ctx, args: argparse.Namespace) -> int:
    require_worktree(ctx)
    if rebase_in_progress(ctx.worktree):
        raise PatchsetError("a rebase is in progress; finish it first")
    if is_dirty(ctx) and not args.check:
        warn("worktree has uncommitted changes; they are not exported (commit them with new/fixup)")
    exports = build_exports(ctx)
    series_text = "".join(f"{name}.patch\n" for name, _ in exports)
    wanted = {patch_path(ctx, name): data for name, data in exports}
    existing = set(ctx.patches_dir.rglob("*.patch")) if ctx.patches_dir.exists() else set()

    if args.check:
        problems = []
        current_series = ctx.series_path.read_text(encoding="utf-8") if ctx.series_path.exists() else ""
        if current_series != series_text:
            problems.append("series differs from the worktree stack")
        for path, data in wanted.items():
            if not path.exists():
                problems.append(f"missing {path.relative_to(ctx.root)}")
            elif path.read_bytes() != data:
                problems.append(f"stale {path.relative_to(ctx.root)}")
        for path in sorted(existing - set(wanted)):
            problems.append(f"orphan {path.relative_to(ctx.root)}")
        if problems:
            print("patches/ does not match the worktree stack:\n  " + "\n  ".join(problems))
            print("run `patchset export`")
            return 1
        info("patches/ matches the worktree stack")
        return 0

    changed = 0
    for path, data in wanted.items():
        if write_bytes_if_changed(path, data):
            changed += 1
            info(f"  wrote {path.relative_to(ctx.root)}")
    for path in sorted(existing - set(wanted)):
        path.unlink()
        changed += 1
        info(f"  removed {path.relative_to(ctx.root)}")
    for group_dir in ctx.patches_dir.glob("*"):
        if group_dir.is_dir() and not any(group_dir.iterdir()):
            group_dir.rmdir()
    if not ctx.series_path.exists() or ctx.series_path.read_text(encoding="utf-8") != series_text:
        write_text_lf(ctx.series_path, series_text)
        changed += 1
    info(f"exported {len(exports)} patch(es), {changed} file(s) changed")
    return 0


def cmd_status(ctx: Ctx, args: argparse.Namespace) -> int:
    props = read_properties(ctx.props_path)
    info(f"upstream: {props.get('upstream.url', '?')} @ {props.get('upstream.commit', '?')[:12]} "
         f"({props.get('upstream.version', '?')})")
    series = read_series(ctx)
    if not (ctx.worktree / ".git").exists():
        info(f"worktree: missing ({ctx.worktree}); run `patchset setup`")
        info(f"series: {len(series)} patches")
        return 0
    base = git(ctx.worktree, "rev-parse", f"{BASE_TAG}^{{commit}}", check=False)
    info(f"worktree: {ctx.worktree} (base {base[:12]})")
    if rebase_in_progress(ctx.worktree):
        info("REBASE IN PROGRESS — resolve conflicts, then `patchset continue` (or `patchset abort`)")
        return 0
    entries = stack_entries(ctx)
    for sha, name in entries:
        stat = git(ctx.worktree, "show", "--numstat", "--format=", sha)
        added = removed = files = 0
        for line in stat.splitlines():
            parts = line.split("\t")
            if len(parts) >= 3:
                files += 1
                added += int(parts[0]) if parts[0].isdigit() else 0
                removed += int(parts[1]) if parts[1].isdigit() else 0
        path = patch_path(ctx, name)
        mark = " "
        if not path.exists() or export_commit(ctx, sha) != path.read_bytes():
            mark = "*"
        info(f" {mark} {name:<48} +{added:<5} -{removed:<5} {files} file(s)")
    if any(n not in {e[1] for e in entries} for n in series):
        info("series contains patches that are not applied in the worktree (run `patchset sync`)")
    if is_dirty(ctx):
        info("worktree has uncommitted changes")
    info("(* = not exported)")
    secrets = ctx.root / "local" / "secrets.properties"
    info(f"local secrets: {'present' if secrets.exists() else 'missing (local/secrets.properties)'}")
    return 0


def validate_name(name: str) -> str:
    if name.endswith(".patch"):
        name = name[:-6]
    if not NAME_RE.match(name):
        raise PatchsetError(f"bad patch name '{name}': use <group>/<kebab-name>, group in {', '.join(GROUPS)}")
    return name


def validate_description(desc: str) -> str:
    desc = desc.strip()
    if not desc or not re.fullmatch(r"[ -~]+", desc):
        raise PatchsetError("description must be non-empty printable ASCII (put details into the body)")
    return desc


def commit_message(name: str, desc: str, body: Optional[str]) -> bytes:
    msg = f"{name}: {desc}"
    if body:
        if re.search(r"(?m)^---", body):
            raise PatchsetError("body must not contain lines starting with '---'")
        msg += "\n\n" + body.strip()
    return (msg + "\n").encode("utf-8")


def cmd_new(ctx: Ctx, args: argparse.Namespace) -> int:
    require_worktree(ctx)
    name = validate_name(args.name)
    desc = validate_description(args.message)
    if any(n == name for _, n in stack_entries(ctx)):
        raise PatchsetError(f"{name} already exists; use `patchset fixup {name}`")
    commit_args = ["commit", "-q", "--no-verify", "--cleanup=verbatim", "-F", "-"]
    if args.all:
        commit_args.insert(1, "-a")
    if not args.all and not git(ctx.worktree, "diff", "--cached", "--name-only"):
        raise PatchsetError("nothing staged (use -a to commit all tracked changes)")
    run(["git", *commit_args], cwd=ctx.worktree, input=commit_message(name, desc, args.body))
    if args.after:
        move_commit(ctx, git(ctx.worktree, "rev-parse", "HEAD"), args.after, args.stop_gradle)
    info(f"created {name}; run `patchset export` when ready")
    return 0


def rebase_with_todo(ctx: Ctx, todo_args: List[str], autosquash: bool = False,
                     extra_env: Optional[Dict[str, str]] = None) -> None:
    script = posix_path(str(Path(__file__).resolve()))
    python = posix_path(sys.executable)
    editor = f'"{python}" "{script}" _todo ' + " ".join(todo_args)
    env = {"GIT_SEQUENCE_EDITOR": editor, "GIT_EDITOR": ":"}
    if extra_env:
        env.update(extra_env)
    args = ["-c", "rebase.instructionFormat=%s", "-c", "rebase.abbreviateCommands=false",
            "rebase", "-i", "--keep-empty", "--empty=keep"]
    args.append("--autosquash" if autosquash else "--no-autosquash")
    args.append(BASE_TAG)
    code = git_passthrough(ctx.worktree, *args, env=env)
    if code != 0 or rebase_in_progress(ctx.worktree):
        raise PatchsetError("rebase stopped (conflict or edit). Resolve it in the worktree, then run "
                            "`patchset continue` (or `patchset abort`).")


def move_commit(ctx: Ctx, sha: str, after: str, stop: bool) -> None:
    stop_gradle(ctx, stop)
    after_sha = "^" if after == "^" else find_commit(ctx, after)
    rebase_with_todo(ctx, ["move", sha, after_sha])


def cmd_fixup(ctx: Ctx, args: argparse.Namespace) -> int:
    require_worktree(ctx)
    target = find_commit(ctx, validate_name(args.name))
    if args.all:
        git(ctx.worktree, "add", "-u")
    if not git(ctx.worktree, "diff", "--cached", "--name-only"):
        raise PatchsetError("nothing staged (use -a to include all tracked changes)")
    run(["git", "commit", "-q", "--no-verify", f"--fixup={target}"], cwd=ctx.worktree)
    stop_gradle(ctx, args.stop_gradle)
    rebase_with_todo(ctx, ["noop"], autosquash=True)
    info(f"folded changes into {args.name}; run `patchset export` when ready")
    return 0


def cmd_edit(ctx: Ctx, args: argparse.Namespace) -> int:
    require_worktree(ctx)
    sha = find_commit(ctx, validate_name(args.name))
    stop_gradle(ctx, args.stop_gradle)
    try:
        rebase_with_todo(ctx, ["edit", sha])
    except PatchsetError:
        pass
    info(f"stopped at {args.name}. Edit files, `git add` them, `git commit --amend --no-edit`, "
         "then run `patchset continue`.")
    return 0


def cmd_drop(ctx: Ctx, args: argparse.Namespace) -> int:
    require_worktree(ctx)
    sha = find_commit(ctx, validate_name(args.name))
    stop_gradle(ctx, args.stop_gradle)
    rebase_with_todo(ctx, ["drop", sha])
    info(f"dropped {args.name}; run `patchset export`")
    return 0


def cmd_move(ctx: Ctx, args: argparse.Namespace) -> int:
    require_worktree(ctx)
    sha = find_commit(ctx, validate_name(args.name))
    after = "^" if args.first else validate_name(args.after or "")
    move_commit(ctx, sha, after, args.stop_gradle)
    info(f"moved {args.name}; run `patchset export`")
    return 0


def reword(ctx: Ctx, name: str, new_message: bytes, stop: bool) -> None:
    sha = find_commit(ctx, name)
    stop_gradle(ctx, stop)
    msg_file = ctx.git_dir / "patchset-reword.txt"
    msg_file.write_bytes(new_message)
    rebase_with_todo(ctx, ["reword", sha, posix_path(str(msg_file))])
    msg_file.unlink()


def cmd_reword(ctx: Ctx, args: argparse.Namespace) -> int:
    require_worktree(ctx)
    name = validate_name(args.name)
    reword(ctx, name, commit_message(name, validate_description(args.message), args.body), args.stop_gradle)
    info(f"reworded {name}; run `patchset export`")
    return 0


def cmd_rename(ctx: Ctx, args: argparse.Namespace) -> int:
    require_worktree(ctx)
    old = validate_name(args.name)
    new = validate_name(args.new_name)
    sha = find_commit(ctx, old)
    full = git(ctx.worktree, "log", "-1", "--format=%B", sha)
    first, _, rest = full.partition("\n")
    desc = first.split(": ", 1)[1] if ": " in first else first
    body = rest.strip() or None
    reword(ctx, old, commit_message(new, desc, body), args.stop_gradle)
    info(f"renamed {old} -> {new}; run `patchset export`")
    return 0


def todo_editor(argv: List[str]) -> int:
    """Invoked by git as GIT_SEQUENCE_EDITOR: `_todo <mode> [args...] <todo-file>`."""
    mode = argv[0]
    todo_path = Path(argv[-1])
    params = argv[1:-1]
    lines = todo_path.read_text(encoding="utf-8").splitlines()
    commands = [ln for ln in lines if ln.strip() and not ln.startswith("#")]
    comments = [ln for ln in lines if not ln.strip() or ln.startswith("#")]

    def matches(line: str, sha: str) -> bool:
        parts = line.split()
        return len(parts) >= 2 and sha.startswith(parts[1])

    if mode == "noop":
        pass
    elif mode == "edit":
        commands = [("edit " + ln.split(" ", 1)[1]) if matches(ln, params[0]) else ln for ln in commands]
    elif mode == "drop":
        commands = [ln for ln in commands if not matches(ln, params[0])]
    elif mode == "move":
        sha, after = params
        moving = [ln for ln in commands if matches(ln, sha)]
        rest = [ln for ln in commands if not matches(ln, sha)]
        if after == "^":
            commands = moving + rest
        else:
            out = []
            for ln in rest:
                out.append(ln)
                if matches(ln, after):
                    out.extend(moving)
            commands = out
    elif mode == "reword":
        sha, msg_file = params
        out = []
        for ln in commands:
            out.append(ln)
            if matches(ln, sha):
                out.append(f'exec git commit --amend -q --no-verify --cleanup=verbatim -F "{msg_file}"')
        commands = out
    else:
        print(f"unknown todo mode {mode}", file=sys.stderr)
        return 2
    todo_path.write_text("\n".join(commands + comments) + "\n", encoding="utf-8")
    return 0


def cmd_continue(ctx: Ctx, args: argparse.Namespace) -> int:
    require_worktree(ctx)
    if rebase_in_progress(ctx.worktree):
        code = git_passthrough(ctx.worktree, "rebase", "--continue", env={"GIT_EDITOR": ":"})
        if code != 0 or rebase_in_progress(ctx.worktree):
            raise PatchsetError("rebase still stopped; resolve the remaining conflicts and run `patchset continue`")
    state = load_state(ctx)
    if state.get("op") == "upstream-rebase":
        finish_upstream_rebase(ctx, state, args)
    info("done")
    return 0


def cmd_abort(ctx: Ctx, args: argparse.Namespace) -> int:
    require_worktree(ctx)
    if rebase_in_progress(ctx.worktree):
        git_passthrough(ctx.worktree, "rebase", "--abort")
    state = load_state(ctx)
    if state.get("op") == "upstream-rebase":
        save_state(ctx, None)
    info("aborted")
    return 0


def cmd_rebase(ctx: Ctx, args: argparse.Namespace) -> int:
    require_worktree(ctx)
    props = upstream_props(ctx)
    if rebase_in_progress(ctx.worktree):
        raise PatchsetError("a rebase is already in progress")
    if is_dirty(ctx):
        raise PatchsetError("worktree has uncommitted changes")
    if cmd_export(ctx, argparse.Namespace(check=True)) != 0:
        raise PatchsetError("the stack has unexported changes; run `patchset export` before rebasing")
    ref = "master" if args.ref == "latest" else args.ref
    url = args.url or props["upstream.url"]
    info(f"fetching {ref} from {url}…")
    new_base = fetch_upstream(ctx.worktree, url, ref)
    old_base = git(ctx.worktree, "rev-parse", f"{BASE_TAG}^{{commit}}")
    if new_base == old_base:
        info("already based on that commit")
        return 0
    state = {"op": "upstream-rebase", "old_base": old_base, "new_base": new_base, "url": url}
    save_state(ctx, state)
    stop_gradle(ctx, args.stop_gradle)
    code = git_passthrough(ctx.worktree, "-c", "rebase.instructionFormat=%s", "rebase", "--empty=drop",
                           "--onto", new_base, old_base, STACK_BRANCH, env={"GIT_EDITOR": ":"})
    if code != 0 or rebase_in_progress(ctx.worktree):
        info("conflicts: resolve them in the worktree (Android Studio works), `git add` the files, then run "
             "`patchset continue`. `patchset abort` restores the previous state.")
        return 1
    finish_upstream_rebase(ctx, state, args)
    return 0


def finish_upstream_rebase(ctx: Ctx, state: Dict[str, str], args: argparse.Namespace) -> None:
    new_base = state["new_base"]
    git(ctx.worktree, "tag", "-f", BASE_TAG, new_base)
    updates = {"upstream.commit": new_base}
    if state.get("url"):
        updates["upstream.url"] = state["url"]
    gradle_props = ctx.worktree / "gradle.properties"
    try:
        upstream_gradle = git(ctx.worktree, "show", f"{new_base}:gradle.properties")
        parsed = {}
        for line in upstream_gradle.splitlines():
            if "=" in line and not line.lstrip().startswith("#"):
                k, v = line.split("=", 1)
                parsed[k.strip()] = v.strip()
        if parsed.get("APP_VERSION_NAME"):
            updates["upstream.version"] = parsed["APP_VERSION_NAME"]
        if parsed.get("APP_VERSION_CODE"):
            updates["upstream.versionCode"] = parsed["APP_VERSION_CODE"]
    except PatchsetError:
        if gradle_props.exists():
            pass
    write_properties(ctx.props_path, updates)
    save_state(ctx, None)
    update_submodules(ctx, ci=False, skip=getattr(args, "no_submodules", False))
    before = {p: p.read_bytes() for p in ctx.patches_dir.rglob("*.patch")} if ctx.patches_dir.exists() else {}
    cmd_export(ctx, argparse.Namespace(check=False))
    report_changed(ctx, before)


def diff_signature(data: bytes) -> List[Tuple[bytes, bytes]]:
    """+/- lines per file, ignoring hunk positions and context: used to tell real changes from drift."""
    sig = []
    current = b""
    try:
        diff = parse_patch(data).diff
    except PatchsetError:
        diff = data
    for line in diff.split(b"\n"):
        m = DIFF_GIT_RE.match(line)
        if m:
            current = m.group(2)
            continue
        if line.startswith((b"+++", b"---", b"index ")):
            continue
        if line.startswith((b"+", b"-")):
            sig.append((current, line))
    return sig


def report_changed(ctx: Ctx, before: Dict[Path, bytes]) -> None:
    after = {p: p.read_bytes() for p in ctx.patches_dir.rglob("*.patch")} if ctx.patches_dir.exists() else {}
    real, drift, gone, new = [], [], [], []
    for path in sorted(set(before) | set(after)):
        rel = str(path.relative_to(ctx.root))
        if path not in after:
            gone.append(rel)
        elif path not in before:
            new.append(rel)
        elif before[path] != after[path]:
            (drift if diff_signature(before[path]) == diff_signature(after[path]) else real).append(rel)
    if gone:
        info("patches that disappeared (became empty or were dropped):\n  " + "\n  ".join(gone))
    if new:
        info("new patches:\n  " + "\n  ".join(new))
    if real:
        info("patches whose content changed — review these:\n  " + "\n  ".join(real))
    if drift:
        info(f"{len(drift)} patch(es) only moved (context/offsets)")
    if not (gone or new or real or drift):
        info("no patch changed")


def cmd_changed(ctx: Ctx, args: argparse.Namespace) -> int:
    ref = args.ref or "HEAD"
    before: Dict[Path, bytes] = {}
    listing = git(ctx.root, "ls-tree", "-r", "--name-only", ref, "--", "patches", check=False)
    for rel in listing.splitlines():
        if rel.endswith(".patch"):
            before[ctx.root / rel] = git_bytes(ctx.root, "show", f"{ref}:{rel}")
    report_changed(ctx, before)
    return 0


def cmd_find(ctx: Ctx, args: argparse.Namespace) -> int:
    require_worktree(ctx)
    target = posix_path(args.path)
    found = False
    for sha, name in stack_entries(ctx):
        out = git(ctx.worktree, "show", "--numstat", "--format=", sha, "--", target)
        for line in out.splitlines():
            parts = line.split("\t")
            if len(parts) >= 3:
                found = True
                info(f"{name:<48} +{parts[0]:<5} -{parts[1]:<5} {parts[2]}")
    if not found:
        info("no patch touches that path")
    return 0


# --------------------------------------------------------------------------------------
# check
# --------------------------------------------------------------------------------------

def touched_paths(data: bytes) -> List[str]:
    paths = []
    for line in data.split(b"\n"):
        m = DIFF_GIT_RE.match(line)
        if m:
            for g in (m.group(1), m.group(2)):
                p = g.decode("utf-8", "replace")
                if p not in paths:
                    paths.append(p)
    return paths


def cmd_check(ctx: Ctx, args: argparse.Namespace) -> int:
    props = upstream_props(ctx)
    series = read_series(ctx)
    drop = validate_name(args.drop) if args.drop else None
    if drop and drop not in series:
        raise PatchsetError(f"{drop} is not in series")
    files = {name: patch_path(ctx, name).read_bytes() for name in series if patch_path(ctx, name).exists()}
    missing = [n for n in series if n not in files]
    problems = [f"missing patch file for {n}" for n in missing]
    orphans = sorted(set(ctx.patches_dir.rglob("*.patch")) - {patch_path(ctx, n) for n in series}) \
        if ctx.patches_dir.exists() else []
    problems += [f"orphan {p.relative_to(ctx.root)} (not in series)" for p in orphans]
    for name, data in files.items():
        try:
            parsed = parse_patch(data, name)
            if parsed.name != name:
                problems.append(f"{name}: subject names '{parsed.name}'")
        except PatchsetError as e:
            problems.append(str(e))
    if problems:
        print("\n".join(problems))
        return 1

    with tempfile.TemporaryDirectory(prefix="patchset-check-") as tmpdir:
        tmp = Path(tmpdir) / "wt"
        tmp.mkdir()
        git(tmp, "init", "-q")
        configure_worktree_repo(tmp, ci=True)
        commit = props["upstream.commit"]
        local = ctx.worktree / ".git" / "objects"
        if local.exists() and git_ok(ctx.worktree, "cat-file", "-e", f"{commit}^{{commit}}"):
            alt = tmp / ".git" / "objects" / "info" / "alternates"
            alt.write_text(str(local.resolve()) + "\n", encoding="utf-8")
            shallow = ctx.worktree / ".git" / "shallow"
            if shallow.exists():
                shutil.copyfile(shallow, tmp / ".git" / "shallow")
        else:
            info(f"fetching {commit[:12]} (blobless)…")
            fetch_upstream(tmp, props["upstream.url"], commit, blobless=True)
        if args.sparse:
            paths = []
            for name in series:
                for p in touched_paths(files[name]):
                    if p not in paths:
                        paths.append(p)
            git(tmp, "sparse-checkout", "init", "--no-cone")
            spec = "\n".join(["/" + p for p in paths] or ["/patchset.nonexistent"]) + "\n"
            sparse_file = tmp / ".git" / "info" / "sparse-checkout"
            sparse_file.write_text(spec, encoding="utf-8")
        git(tmp, "checkout", "-q", "-B", STACK_BRANCH, commit)
        git(tmp, "tag", "-f", BASE_TAG, commit)
        tctx = Ctx(root=ctx.root, worktree=tmp, verbose=ctx.verbose)
        failures = []
        shas = []
        for name in series:
            if name == drop:
                continue
            try:
                shas.append((name, apply_patch_commit(tmp, files[name], name)))
            except PatchsetError as e:
                failures.append(str(e))
                break
        if failures:
            print("\n".join(failures))
            return 1
        if drop:
            info(f"series applies without {drop}")
            return 0
        mismatched = []
        for name, sha in shas:
            if export_commit(tctx, sha) != files[name]:
                mismatched.append(name)
        if mismatched:
            print("patches are not in canonical form (hand-edited?). Re-export them:\n  " + "\n  ".join(mismatched))
            return 1
    info(f"ok: {len(series)} patch(es) apply cleanly and are canonical")
    return 0


# --------------------------------------------------------------------------------------
# lint
# --------------------------------------------------------------------------------------

CODE_EXT = (".java", ".kt", ".c", ".cc", ".cpp", ".h", ".hpp")
FORCED_RE = re.compile(r"\btrue\s*\|\||\|\|\s*true\b|\bfalse\s*&&|&&\s*false\b")
REFLECTION_RE = re.compile(r"getDeclaredField\s*\(|getDeclaredMethod\s*\(|HiddenApiBypass")
API_KEY_RE = re.compile(r"AIza[0-9A-Za-z_\-]{35}")
FORK_IMPORT_RE = re.compile(r"^\s*import\s+(static\s+)?ru\.llacker\.neogram\.")


@dataclass
class FileDiff:
    old: str
    new: str
    new_file: bool = False
    deleted: bool = False
    binary: bool = False
    hunks: List[Tuple[int, int, int, int, List[bytes]]] = field(default_factory=list)


def parse_diff(diff: bytes) -> List[FileDiff]:
    files: List[FileDiff] = []
    cur: Optional[FileDiff] = None
    hunk_lines: Optional[List[bytes]] = None
    for line in diff.split(b"\n"):
        m = DIFF_GIT_RE.match(line)
        if m:
            cur = FileDiff(m.group(1).decode("utf-8", "replace"), m.group(2).decode("utf-8", "replace"))
            files.append(cur)
            hunk_lines = None
            continue
        if cur is None:
            continue
        if line.startswith(b"new file mode"):
            cur.new_file = True
        elif line.startswith(b"deleted file mode"):
            cur.deleted = True
        elif line.startswith(b"GIT binary patch") or line.startswith(b"Binary files"):
            cur.binary = True
        hm = HUNK_RE.match(line)
        if hm:
            hunk_lines = []
            cur.hunks.append((int(hm.group(1)), int(hm.group(2) or 1), int(hm.group(3)), int(hm.group(4) or 1),
                              hunk_lines))
            continue
        if hunk_lines is not None and line[:1] in (b"+", b"-", b" ", b"\\"):
            hunk_lines.append(line)
    return files


def lint_patch(name: str, data: bytes, errors: List[str], warnings: List[str]) -> None:
    try:
        parsed = parse_patch(data, name)
    except PatchsetError as e:
        errors.append(str(e))
        return
    if not SUBJECT_RE.match(parsed.subject):
        errors.append(f"{name}: subject must be '<group>/<name>: <ascii description>'")
    elif parsed.name != name:
        errors.append(f"{name}: subject names '{parsed.name}' but the file is {name}.patch")
    group = name.split("/", 1)[0]
    changed = 0
    for fd in parse_diff(parsed.diff):
        path = fd.new if not fd.deleted else fd.old
        if fd.binary:
            errors.append(f"{name}: binary diff in {path} (binaries belong in fork/)")
        if fd.new_file and path.startswith("TMessagesProj/src/main/java/org/telegram/"):
            errors.append(f"{name}: new stock-package file {path} — fork code belongs in fork/java")
        if path.endswith(("build.gradle", "build.gradle.kts", "settings.gradle")) and not (
                name.startswith("misc/build-") or name.startswith("debloat/")):
            warnings.append(f"{name}: touches {path}; build changes belong in misc/build-* patches")
        for (_, _, _, _, lines) in fd.hunks:
            plus = [ln[1:] for ln in lines if ln.startswith(b"+")]
            minus = [ln[1:] for ln in lines if ln.startswith(b"-")]
            changed += len(plus) + len(minus)
            if plus and minus and sorted(re.sub(rb"\s+", b"", x) for x in plus) == \
                    sorted(re.sub(rb"\s+", b"", x) for x in minus):
                warnings.append(f"{name}: whitespace-only hunk in {path}")
            comment_run = 0
            for raw in plus:
                text = raw.decode("utf-8", "replace")
                if path.endswith(CODE_EXT):
                    if FORCED_RE.search(text):
                        errors.append(f"{name}: forced condition in {path}: {text.strip()}")
                    if REFLECTION_RE.search(text):
                        errors.append(f"{name}: reflection/hidden-API access in {path}: {text.strip()}")
                    if FORK_IMPORT_RE.search(text):
                        errors.append(f"{name}: import of fork code in {path}; call it by fully-qualified name")
                    stripped = text.strip()
                    if stripped.startswith("//") and re.search(r"[;{}]\s*$", stripped):
                        comment_run += 1
                        if comment_run == 5:
                            warnings.append(f"{name}: commented-out code block in {path}")
                    else:
                        comment_run = 0
                if API_KEY_RE.search(text):
                    errors.append(f"{name}: looks like an API key in {path}")
    budget = HOOK_BUDGET.get(group)
    if budget and changed > budget:
        warnings.append(f"{name}: {changed} changed lines exceeds the {group} budget of {budget}")
    if re.search(r"(?m)^---", parsed.body):
        errors.append(f"{name}: commit body must not contain lines starting with '---'")


def overlap_report(ctx: Ctx, errors: List[str], warnings: List[str]) -> None:
    """Warn when a later patch rewrites lines owned by an earlier one; error when it reverts it entirely."""
    owners: Dict[str, List[List]] = {}  # path -> list of [start, end, owner] for owned new-side lines
    added_counts: Dict[str, int] = {}
    removed_by: Dict[Tuple[str, str], int] = {}
    for sha, name in stack_entries(ctx):
        diff = git_bytes(ctx.worktree, "-c", "diff.renames=false", "show", "--format=", "-U0", "--no-color",
                         "--no-ext-diff", sha)
        for fd in parse_diff(diff):
            path = fd.new if not fd.deleted else fd.old
            intervals = owners.setdefault(path, [])
            delta_total = 0
            new_intervals = []
            for (old_start, old_len, new_start, new_len, lines) in fd.hunks:
                # lines [old_start, old_start+old_len) of the previous version are replaced
                lo, hi = old_start, old_start + old_len
                if old_len == 0:
                    lo = hi = old_start + 1
                for iv in intervals:
                    if iv[2] != name and old_len and iv[0] < hi and lo < iv[1]:
                        overlap = min(iv[1], hi) - max(iv[0], lo)
                        key = (iv[2], name)
                        removed_by[key] = removed_by.get(key, 0) + overlap
                added = sum(1 for ln in lines if ln.startswith(b"+"))
                if added:
                    added_counts[name] = added_counts.get(name, 0) + added
                    new_intervals.append([new_start, new_start + new_len, name])
                delta_total += new_len - old_len
            # rebuild ownership map by shifting old intervals through the hunks
            shifted = []
            for iv in intervals:
                start, end, owner = iv
                shift = 0
                keep = True
                for (old_start, old_len, new_start, new_len, _) in fd.hunks:
                    hunk_old_start = old_start if old_len else old_start + 1
                    hunk_old_end = hunk_old_start + old_len
                    if end <= hunk_old_start:
                        break
                    if start >= hunk_old_end:
                        shift += new_len - old_len
                        continue
                    keep = False
                    break
                if keep:
                    shifted.append([start + shift, end + shift, owner])
            owners[path] = shifted + new_intervals
    for (owner, by), count in sorted(removed_by.items()):
        total = added_counts.get(owner, 0)
        if total and count >= total:
            errors.append(f"{by} rewrites every line added by {owner} (revert?)")
        else:
            warnings.append(f"{by} modifies {count} line(s) added by {owner}")


def fork_files(root: Path) -> List[Path]:
    """Files under fork/ that git tracks or would track: build outputs and other ignored files are skipped."""
    fork = root / "fork"
    if git_ok(root, "rev-parse", "--is-inside-work-tree"):
        out = git_bytes(root, "ls-files", "-z", "--cached", "--others", "--exclude-standard", "--", "fork")
        return sorted(root / name for name in out.decode("utf-8", "surrogateescape").split("\0") if name)
    return sorted(path for path in fork.rglob("*") if path.is_file())


def lint_fork_tree(ctx: Ctx, errors: List[str], warnings: List[str], stock_keys: Optional[set]) -> None:
    fork = ctx.root / "fork"
    if not fork.exists():
        return
    for path in fork_files(ctx.root):
        if not path.is_file() or path.suffix in (".png", ".webp", ".jpg", ".jpeg", ".ttf", ".otf", ".so", ".jar",
                                                 ".jks", ".keystore", ".gif", ".bin", ".zip", ".mp3", ".ogg"):
            continue
        if path.suffix in (".cmd", ".bat"):
            continue
        data = path.read_bytes()
        if b"\r" in data:
            errors.append(f"{path.relative_to(ctx.root)}: CR line endings (use LF)")
        if path.suffix == ".java":
            text = data.decode("utf-8", "replace")
            if FORCED_RE.search(text):
                warnings.append(f"{path.relative_to(ctx.root)}: forced condition (true || / false &&)")
            if API_KEY_RE.search(text):
                errors.append(f"{path.relative_to(ctx.root)}: looks like an API key")
    res = fork / "res"
    default = res / "values" / "strings_neo.xml"
    if not default.exists():
        return
    key_re = re.compile(r'<string\s+name="([^"]+)"')
    default_text = default.read_text(encoding="utf-8")
    default_keys = set(key_re.findall(default_text))
    for key in sorted(default_keys):
        if not key.startswith("Neo"):
            errors.append(f"strings_neo.xml: key '{key}' must start with 'Neo'")
        if stock_keys and key in stock_keys:
            errors.append(f"strings_neo.xml: key '{key}' collides with a stock string")
    for loc in sorted(res.glob("values*/strings_neo.xml")):
        text = re.sub(r"<!--.*?-->", "", loc.read_text(encoding="utf-8"), flags=re.S)
        rel = loc.relative_to(ctx.root)
        if "<plurals" in text:
            errors.append(f"{rel}: <plurals> is not supported by the Telegram strings pipeline; use _one/_other keys")
        if re.search(r">\s*@string/", text):
            errors.append(f"{rel}: @string/ references are not resolved by the Telegram strings pipeline")
        for key, value in STRING_VALUE_RE.findall(text):
            if not (value.startswith('"') and value.endswith('"')) and UNESCAPED_QUOTE_RE.search(value):
                errors.append(f"{rel}: string '{key}' has an unescaped apostrophe or quote (use \\' and \\\")")
        for brand in ("lawXgram", "lawxgram", "локсграм", "Neogram"):
            if brand in text:
                errors.append(f"{rel}: literal brand '{brand}'; pass the app name as a format argument")
                break
        if loc != default:
            extra = set(key_re.findall(text)) - default_keys
            for key in sorted(extra):
                warnings.append(f"{rel}: key '{key}' is not in the default strings_neo.xml")


STRING_VALUE_RE = re.compile(r'<string\s+name="([^"]+)"[^>]*>(.*?)</string>', re.S)
# aapt rejects ' and " in a string resource unless escaped or the whole value is quoted
UNESCAPED_QUOTE_RE = re.compile(r"(?<!\\)['\"]")


def stock_string_keys(ctx: Ctx) -> Optional[set]:
    path = ctx.worktree / "TMessagesProj" / "src" / "main" / "res" / "values" / "strings.xml"
    if not path.exists():
        return None
    return set(re.findall(r'<string\s+name="([^"]+)"', path.read_text(encoding="utf-8")))


def cmd_lint(ctx: Ctx, args: argparse.Namespace) -> int:
    errors: List[str] = []
    warnings: List[str] = []
    series = read_series(ctx)
    for name in series:
        path = patch_path(ctx, name)
        if not path.exists():
            errors.append(f"series lists {name} but the file is missing")
            continue
        lint_patch(name, path.read_bytes(), errors, warnings)
    if ctx.patches_dir.exists():
        for path in sorted(ctx.patches_dir.rglob("*.patch")):
            rel = path.relative_to(ctx.patches_dir).as_posix()[: -len(".patch")]
            if rel not in series:
                errors.append(f"patches/{rel}.patch is not listed in series")
    have_worktree = (ctx.worktree / ".git").exists() and not rebase_in_progress(ctx.worktree)
    if not args.fast and have_worktree:
        try:
            overlap_report(ctx, errors, warnings)
        except PatchsetError as e:
            warnings.append(f"overlap analysis skipped: {e}")
    lint_fork_tree(ctx, errors, warnings, stock_string_keys(ctx) if have_worktree else None)
    for w in warnings:
        print(f"warning: {w}")
    for e in errors:
        print(f"error: {e}")
    if errors or (args.strict and warnings and args.warnings_as_errors):
        return 1
    info(f"lint: {len(errors)} error(s), {len(warnings)} warning(s)")
    return 0


# --------------------------------------------------------------------------------------
# doctor / install-hooks
# --------------------------------------------------------------------------------------

def cmd_doctor(ctx: Ctx, args: argparse.Namespace) -> int:
    ok = True

    def report(good: bool, msg: str) -> None:
        nonlocal ok
        ok = ok and good
        print(("  ok   " if good else "  FAIL ") + msg)

    report(sys.version_info >= (3, 9), f"python {sys.version.split()[0]} (>= 3.9)")
    gv = run(["git", "--version"], check=False)
    version = gv.stdout.decode().strip() if gv.returncode == 0 else "missing"
    m = re.search(r"(\d+)\.(\d+)", version)
    report(bool(m) and (int(m.group(1)), int(m.group(2))) >= (2, 35), f"{version} (>= 2.35)")
    java = shutil.which("java")
    report(java is not None, f"java: {java or 'not found (JDK 17+ required)'}")
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    report(bool(sdk), f"ANDROID_HOME: {sdk or 'not set'}")
    if sdk:
        report((Path(sdk) / "ndk" / "27.2.12479018").exists(), "NDK 27.2.12479018")
        report((Path(sdk) / "cmake" / "3.22.1").exists(), "CMake 3.22.1")
        report((Path(sdk) / "build-tools" / "36.0.0").exists(), "build-tools 36.0.0")
    if os.name == "nt":
        reg = run(["reg", "query", r"HKLM\SYSTEM\CurrentControlSet\Control\FileSystem", "/v",
                   "LongPathsEnabled"], check=False)
        report(b"0x1" in (reg.stdout or b""), "Windows LongPathsEnabled=1")
        report(len(str(ctx.worktree)) <= 40,
               f"worktree path length {len(str(ctx.worktree))} (<= 40 recommended; use --worktree C:\\w\\neo)")
        autocrlf = git(ctx.root, "config", "--global", "core.autocrlf", check=False)
        report(autocrlf in ("", "false", "input"), f"global core.autocrlf={autocrlf or 'unset'}")
    else:
        report(shutil.which("nasm") is not None, "nasm (needed for x86/x86_64 native builds)")
    return 0 if ok else 1


def cmd_install_hooks(ctx: Ctx, args: argparse.Namespace) -> int:
    hooks = Path(git(ctx.root, "rev-parse", "--git-path", "hooks"))
    if not hooks.is_absolute():
        hooks = ctx.root / hooks
    hook = hooks / "pre-commit"
    write_text_lf(hook, (
        "#!/bin/sh\n"
        "# Installed by tools/patchset.py install-hooks\n"
        'ROOT="$(git rev-parse --show-toplevel)"\n'
        'PY=python3; command -v python3 >/dev/null 2>&1 || PY=python\n'
        'if [ -d "$ROOT/worktree/.git" ]; then\n'
        '  "$PY" "$ROOT/tools/patchset.py" export --check || exit 1\n'
        'fi\n'
        '"$PY" "$ROOT/tools/patchset.py" lint --fast || exit 1\n'))
    try:
        os.chmod(hook, 0o755)
    except OSError:
        pass
    info(f"installed {hook}")
    return 0


# --------------------------------------------------------------------------------------
# CLI
# --------------------------------------------------------------------------------------

def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="patchset", description=__doc__.split("\n\n")[0])
    p.add_argument("--root", help="patchset repository root (default: parent of tools/)")
    p.add_argument("--worktree", help="worktree directory (default: <root>/worktree or $NEO_WORKTREE)")
    p.add_argument("-v", "--verbose", action="store_true")
    sub = p.add_subparsers(dest="command", required=True)

    def stop_flag(sp):
        sp.add_argument("--no-stop-gradle", dest="stop_gradle", action="store_false",
                        help="do not stop Gradle daemons before rewriting history (Windows)")

    s = sub.add_parser("setup", help="create or update the worktree from patches/")
    s.add_argument("--ci", action="store_true", help="CI mode: shallow submodules, no rerere")
    s.add_argument("--no-submodules", action="store_true")
    s.add_argument("--force", action="store_true", help="rebuild diverged commits from patches/")
    s.set_defaults(func=cmd_setup)

    s = sub.add_parser("sync", help="bring the worktree in line with patches/ after a pull")
    s.add_argument("--force", action="store_true")
    s.set_defaults(func=cmd_sync)

    s = sub.add_parser("export", help="write worktree commits to patches/ and series")
    s.add_argument("--check", action="store_true", help="only verify, exit 1 when out of date")
    s.set_defaults(func=cmd_export)

    s = sub.add_parser("status", help="show the stack")
    s.set_defaults(func=cmd_status)

    s = sub.add_parser("new", help="commit staged changes as a new patch")
    s.add_argument("name")
    s.add_argument("-m", "--message", required=True, help="short ASCII description")
    s.add_argument("--body", help="longer description (any language)")
    s.add_argument("-a", "--all", action="store_true", help="commit all tracked changes")
    s.add_argument("--after", help="place the new patch right after this one ('^' = first)")
    stop_flag(s)
    s.set_defaults(func=cmd_new)

    s = sub.add_parser("fixup", help="fold staged changes into an existing patch")
    s.add_argument("name")
    s.add_argument("-a", "--all", action="store_true")
    stop_flag(s)
    s.set_defaults(func=cmd_fixup)

    s = sub.add_parser("edit", help="stop the stack at a patch to amend it")
    s.add_argument("name")
    stop_flag(s)
    s.set_defaults(func=cmd_edit)

    s = sub.add_parser("drop", help="remove a patch from the stack")
    s.add_argument("name")
    stop_flag(s)
    s.set_defaults(func=cmd_drop)

    s = sub.add_parser("move", help="reorder a patch")
    s.add_argument("name")
    g = s.add_mutually_exclusive_group(required=True)
    g.add_argument("--after")
    g.add_argument("--first", action="store_true")
    stop_flag(s)
    s.set_defaults(func=cmd_move)

    s = sub.add_parser("reword", help="change a patch description")
    s.add_argument("name")
    s.add_argument("-m", "--message", required=True)
    s.add_argument("--body")
    stop_flag(s)
    s.set_defaults(func=cmd_reword)

    s = sub.add_parser("rename", help="rename a patch (group/name)")
    s.add_argument("name")
    s.add_argument("new_name")
    stop_flag(s)
    s.set_defaults(func=cmd_rename)

    s = sub.add_parser("rebase", help="move the stack onto another upstream commit")
    s.add_argument("ref", help="commit sha, branch, or 'latest'")
    s.add_argument("--url", help="upstream repository url (default: from patchset.properties)")
    s.add_argument("--no-submodules", action="store_true")
    stop_flag(s)
    s.set_defaults(func=cmd_rebase)

    s = sub.add_parser("continue", help="continue a stopped rebase/edit")
    s.add_argument("--no-submodules", action="store_true")
    s.set_defaults(func=cmd_continue)

    s = sub.add_parser("abort", help="abort a stopped rebase/edit")
    s.set_defaults(func=cmd_abort)

    s = sub.add_parser("changed", help="classify patch changes vs a git ref of this repo")
    s.add_argument("ref", nargs="?", help="default: HEAD")
    s.set_defaults(func=cmd_changed)

    s = sub.add_parser("find", help="list patches touching a path")
    s.add_argument("path")
    s.set_defaults(func=cmd_find)

    s = sub.add_parser("check", help="apply series from scratch in a temp dir and verify canonical form")
    s.add_argument("--sparse", action="store_true", help="check out only the files the patches touch")
    s.add_argument("--drop", help="check that the series still applies without this patch")
    s.set_defaults(func=cmd_check)

    s = sub.add_parser("lint", help="static checks for patches and fork resources")
    s.add_argument("--fast", action="store_true", help="skip checks that need the worktree")
    s.add_argument("--strict", action="store_true")
    s.add_argument("--warnings-as-errors", action="store_true")
    s.set_defaults(func=cmd_lint)

    s = sub.add_parser("doctor", help="check the local toolchain")
    s.set_defaults(func=cmd_doctor)

    s = sub.add_parser("install-hooks", help="install a pre-commit hook in this repository")
    s.set_defaults(func=cmd_install_hooks)
    return p


def main(argv: Optional[List[str]] = None) -> int:
    argv = list(sys.argv[1:] if argv is None else argv)
    if argv and argv[0] == "_todo":
        return todo_editor(argv[1:])
    parser = build_parser()
    args = parser.parse_args(argv)
    root = Path(args.root or os.environ.get("PATCHSET_ROOT") or Path(__file__).resolve().parent.parent).resolve()
    worktree = Path(args.worktree or os.environ.get("NEO_WORKTREE") or (root / "worktree"))
    if not worktree.is_absolute():
        worktree = (Path.cwd() / worktree).resolve()
    ctx = Ctx(root=root, worktree=worktree, verbose=args.verbose)
    for attr in ("stop_gradle",):
        if not hasattr(args, attr):
            setattr(args, attr, True)
    try:
        return args.func(ctx, args)
    except PatchsetError as e:
        print(f"error: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
