"""Tests for tools/patchset.py. They use real git repositories in temporary directories.

Run: python -m unittest discover -s tools/tests -v
"""

from __future__ import annotations

import importlib.util
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

TOOL = Path(__file__).resolve().parents[1] / "patchset.py"

_spec = importlib.util.spec_from_file_location("patchset", TOOL)
patchset = importlib.util.module_from_spec(_spec)
sys.modules["patchset"] = patchset
_spec.loader.exec_module(patchset)

ENV = {
    "GIT_AUTHOR_NAME": "Upstream", "GIT_AUTHOR_EMAIL": "up@example.invalid",
    "GIT_COMMITTER_NAME": "Upstream", "GIT_COMMITTER_EMAIL": "up@example.invalid",
}


def git(cwd, *args, check=True):
    env = os.environ.copy()
    env.update(ENV)
    proc = subprocess.run(["git", *args], cwd=str(cwd), env=env, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE)
    if check and proc.returncode != 0:
        raise AssertionError(f"git {' '.join(args)} failed:\n{proc.stdout.decode('utf-8', 'replace')}{proc.stderr.decode('utf-8', 'replace')}")
    return proc.stdout.decode("utf-8", "replace").strip()


def write(path: Path, data, newline="\n"):
    path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(data, bytes):
        path.write_bytes(data)
    else:
        with open(path, "w", encoding="utf-8", newline=newline) as fh:
            fh.write(data)


def file_url(path: Path) -> str:
    return path.resolve().as_uri()


class Fixture:
    def __init__(self, tmp: Path):
        self.tmp = tmp
        self.sub = tmp / "subrepo"
        self.upstream = tmp / "upstream"
        self.root = tmp / "ps"
        self.wt = self.root / "worktree"
        self._make_submodule_repo()
        self._make_upstream()
        self._make_root()

    def _make_submodule_repo(self):
        self.sub.mkdir()
        git(self.sub, "init", "-q")
        write(self.sub / "lib.txt", "library\n")
        git(self.sub, "add", ".")
        git(self.sub, "commit", "-q", "-m", "lib")
        git(self.sub, "config", "uploadpack.allowAnySHA1InWant", "true")

    def _make_upstream(self):
        up = self.upstream
        up.mkdir()
        git(up, "init", "-q")
        git(up, "config", "uploadpack.allowAnySHA1InWant", "true")
        write(up / "README.md", "upstream readme\n")
        write(up / "src" / "Main.java", "class Main {\n    void a() {}\n    void b() {}\n    void c() {}\n}\n")
        write(up / "src" / "Other.java", "class Other {\n    int x = 1;\n}\n")
        write(up / "src" / "crlf.txt", "line1\nline2\nline3\n", newline="\r\n")
        write(up / "assets" / "blob.bin", bytes(range(256)))
        write(up / "nested" / "deep" / "dir" / "file.txt", "nested\n")
        git(up, "add", ".")
        git(up, "-c", "protocol.file.allow=always", "submodule", "add", "-q", file_url(self.sub), "libs/sub")
        git(up, "commit", "-q", "-m", "base")
        self.base = git(up, "rev-parse", "HEAD")

    def _make_root(self):
        self.root.mkdir()
        git(self.root, "init", "-q")
        write(self.root / "patchset.properties",
              "# test\n"
              f"upstream.url={file_url(self.upstream)}\n"
              f"upstream.commit={self.base}\n"
              "upstream.version=1.0\n")
        write(self.root / "series", "")
        (self.root / "patches").mkdir()

    def tool(self, *args, worktree=None, check=True, input=None):
        cmd = [sys.executable, str(TOOL), "--root", str(self.root)]
        if worktree:
            cmd += ["--worktree", str(worktree)]
        cmd += list(args)
        env = os.environ.copy()
        env["GIT_ALLOW_PROTOCOL"] = "file:https:ssh:git"
        proc = subprocess.run(cmd, cwd=str(self.root), env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                              input=input)
        # the tool prints UTF-8; git messages may come in the console code page on Windows
        out = proc.stdout.decode("utf-8", "replace") + proc.stderr.decode("utf-8", "replace")
        if check and proc.returncode != 0:
            raise AssertionError(f"patchset {' '.join(args)} failed ({proc.returncode}):\n{out}")
        return proc.returncode, out

    def upstream_commit(self, rel, content, message="upstream change", newline="\n"):
        write(self.upstream / rel, content, newline=newline)
        git(self.upstream, "add", rel)
        git(self.upstream, "commit", "-q", "-m", message)
        return git(self.upstream, "rev-parse", "HEAD")

    def patch_bytes(self, name):
        return (self.root / "patches" / f"{name}.patch").read_bytes()

    def series(self):
        return (self.root / "series").read_text(encoding="utf-8").split()


class PatchsetTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.mkdtemp(prefix="patchset-test-")
        self.f = Fixture(Path(self._tmp))

    def tearDown(self):
        def onerror(func, path, exc):
            os.chmod(path, 0o700)
            func(path)
        shutil.rmtree(self._tmp, onerror=onerror)

    # helpers -------------------------------------------------------------------------
    def setup_wt(self, worktree=None):
        self.f.tool("setup", worktree=worktree)
        return worktree or self.f.wt

    def make_patch(self, name, rel, content, message="change", newline="\n", after=None, worktree=None):
        wt = worktree or self.f.wt
        write(wt / rel, content, newline=newline)
        git(wt, "add", "-A", rel)
        args = ["new", name, "-m", message]
        if after:
            args += ["--after", after]
        self.f.tool(*args, worktree=worktree)

    # tests ---------------------------------------------------------------------------
    def test_setup_creates_worktree_with_submodules(self):
        self.setup_wt()
        self.assertEqual(git(self.f.wt, "rev-parse", "HEAD"), self.f.base)
        self.assertEqual(git(self.f.wt, "rev-parse", "patchset/base^{commit}"), self.f.base)
        self.assertTrue((self.f.wt / "libs" / "sub" / "lib.txt").exists())
        self.assertTrue((self.f.wt / "neo-build.properties").exists())
        self.assertIn("neo.root=..", (self.f.wt / "neo-build.properties").read_text())
        self.assertEqual(git(self.f.wt, "config", "core.autocrlf"), "false")

    def test_export_format_is_canonical(self):
        self.setup_wt()
        write(self.f.wt / "src" / "Main.java", "class Main {\n    void a() {}\n    void B() {}\n    void c() {}\n}\n")
        write(self.f.wt / "src" / "crlf.txt", "line1\nLINE2\nline3\n", newline="\r\n")
        self.f.tool("new", "feature/demo", "-m", "demo change", "-a", "--body", "Описание на русском\nвторая строка")
        self.f.tool("export")
        data = self.f.patch_bytes("feature/demo")
        self.assertTrue(data.startswith(patchset.ZERO_FROM + b"\n"))
        self.assertIn(b"From: Neogram Patchset <patches@neogram.invalid>\n", data)
        self.assertNotIn(b"\nDate: ", data)
        self.assertIn(b"Subject: feature/demo: demo change\n", data)
        self.assertIn("Описание на русском".encode(), data)
        self.assertIn(b"index 0000000..0000000 100644", data)
        self.assertIn(b"+LINE2\r\n", data)
        self.assertEqual(self.f.series(), ["feature/demo.patch"])
        self.assertEqual(self.f.tool("export", "--check")[0], 0)
        self.assertEqual(self.f.tool("check")[0], 0)
        self.assertEqual(self.f.tool("check", "--sparse")[0], 0)

    def test_long_subject_is_unfolded_and_roundtrips(self):
        self.setup_wt()
        desc = "a fairly long description that definitely exceeds the seventy eight column limit of mail headers"
        self.make_patch("feature/long-name-for-folding", "README.md", "changed\n", message=desc)
        self.f.tool("export")
        data = self.f.patch_bytes("feature/long-name-for-folding")
        self.assertIn(f"Subject: feature/long-name-for-folding: {desc}\n".encode(), data)
        self.assertEqual(self.f.tool("check")[0], 0)

    def test_setup_is_deterministic(self):
        self.setup_wt()
        self.make_patch("feature/one", "src/Other.java", "class Other {\n    int x = 2;\n}\n")
        self.make_patch("bugfix/two", "README.md", "fixed readme\n")
        self.f.tool("export")
        wt2 = self.f.tmp / "wt2"
        wt3 = self.f.tmp / "wt3"
        self.f.tool("setup", "--no-submodules", worktree=wt2)
        self.f.tool("setup", "--no-submodules", worktree=wt3)
        self.assertEqual(git(wt2, "rev-parse", "HEAD"), git(wt3, "rev-parse", "HEAD"))
        self.assertEqual(self.f.tool("export", "--check", worktree=wt2)[0], 0)

    def test_check_detects_hand_edited_patch(self):
        self.setup_wt()
        self.make_patch("feature/one", "src/Other.java", "class Other {\n    int x = 2;\n}\n")
        self.f.tool("export")
        path = self.f.root / "patches" / "feature" / "one.patch"
        # a header that export never writes: applies fine, but is not canonical
        path.write_bytes(path.read_bytes().replace(b"\nSubject: ", b"\nDate: Mon, 1 Jan 2001 00:00:00 +0000\nSubject: ", 1))
        code, out = self.f.tool("check", check=False)
        self.assertEqual(code, 1, out)
        self.assertIn("canonical", out)

    def test_fixup_and_ordering(self):
        self.setup_wt()
        self.make_patch("feature/a", "src/Main.java",
                        "class Main {\n    void A() {}\n    void b() {}\n    void c() {}\n}\n")
        self.make_patch("feature/b", "src/Other.java", "class Other {\n    int x = 3;\n}\n")
        write(self.f.wt / "src" / "Main.java", "class Main {\n    void A() {}\n    void b() {}\n    void C() {}\n}\n")
        self.f.tool("fixup", "feature/a", "-a")
        self.make_patch("feature/c", "README.md", "readme c\n", after="feature/a")
        self.f.tool("export")
        self.assertEqual(self.f.series(), ["feature/a.patch", "feature/c.patch", "feature/b.patch"])
        a = self.f.patch_bytes("feature/a")
        self.assertIn(b"+    void A() {}", a)
        self.assertIn(b"+    void C() {}", a)
        self.assertEqual(self.f.tool("check")[0], 0)

    def test_rename_reword_move_drop(self):
        self.setup_wt()
        self.make_patch("feature/a", "README.md", "a\n")
        self.make_patch("feature/b", "src/Other.java", "class Other {\n    int x = 4;\n}\n")
        self.f.tool("rename", "feature/b", "debloat/b2")
        self.f.tool("reword", "feature/a", "-m", "new description")
        self.f.tool("move", "debloat/b2", "--first")
        self.f.tool("export")
        self.assertEqual(self.f.series(), ["debloat/b2.patch", "feature/a.patch"])
        self.assertIn(b"Subject: feature/a: new description\n", self.f.patch_bytes("feature/a"))
        self.f.tool("drop", "feature/a")
        self.f.tool("export")
        self.assertEqual(self.f.series(), ["debloat/b2.patch"])
        self.assertFalse((self.f.root / "patches" / "feature" / "a.patch").exists())

    def test_lint_rules(self):
        self.setup_wt()
        write(self.f.wt / "src" / "Main.java",
              "import ru.llacker.neogram.NeoConfig;\nclass Main {\n    void a() { if (true || x) {} }\n"
              "    void b() {}\n    void c() {}\n}\n")
        write(self.f.wt / "TMessagesProj" / "src" / "main" / "java" / "org" / "telegram" / "New.java", "class New {}\n")
        git(self.f.wt, "add", "-A")
        self.f.tool("new", "feature/bad", "-m", "bad patch")
        self.f.tool("export")
        code, out = self.f.tool("lint", check=False)
        self.assertEqual(code, 1, out)
        self.assertIn("forced condition", out)
        self.assertIn("fully-qualified", out)
        self.assertIn("new stock-package file", out)

    def test_binary_diff_is_rejected(self):
        self.setup_wt()
        write(self.f.wt / "assets" / "blob.bin", bytes(reversed(range(256))))
        git(self.f.wt, "add", "-A")
        self.f.tool("new", "misc/binary", "-m", "binary change")
        code, out = self.f.tool("export", check=False)
        self.assertEqual(code, 1, out)
        self.assertIn("binary", out)

    def test_overlap_lint(self):
        self.setup_wt()
        self.make_patch("feature/a", "src/Other.java", "class Other {\n    int x = 1;\n    int y = 5;\n}\n")
        self.make_patch("feature/b", "src/Other.java", "class Other {\n    int x = 1;\n    int y = 6;\n}\n")
        self.f.tool("export")
        code, out = self.f.tool("lint", check=False)
        self.assertIn("feature/b rewrites every line added by feature/a", out)

    def test_rebase_clean_and_with_conflict(self):
        self.setup_wt()
        self.make_patch("feature/a", "src/Other.java", "class Other {\n    int x = 10;\n}\n")
        self.f.tool("export")
        git(self.f.root, "add", "-A")
        git(self.f.root, "commit", "-q", "-m", "patches")
        clean = self.f.upstream_commit("README.md", "new upstream readme\n")
        self.f.tool("rebase", clean)
        props = patchset.read_properties(self.f.root / "patchset.properties")
        self.assertEqual(props["upstream.commit"], clean)
        self.assertEqual(git(self.f.wt, "rev-parse", "patchset/base^{commit}"), clean)
        self.assertEqual(self.f.tool("export", "--check")[0], 0)

        conflicting = self.f.upstream_commit("src/Other.java", "class Other {\n    int x = 20;\n}\n")
        code, out = self.f.tool("rebase", conflicting, check=False)
        self.assertEqual(code, 1, out)
        write(self.f.wt / "src" / "Other.java", "class Other {\n    int x = 30;\n}\n")
        git(self.f.wt, "add", "src/Other.java")
        code, out = self.f.tool("continue")
        self.assertIn("content changed", out)
        props = patchset.read_properties(self.f.root / "patchset.properties")
        self.assertEqual(props["upstream.commit"], conflicting)
        self.assertIn(b"+    int x = 30;", self.f.patch_bytes("feature/a"))
        self.assertEqual(self.f.tool("check")[0], 0)

    def test_sync_after_patches_change(self):
        self.setup_wt()
        self.make_patch("feature/a", "src/Other.java", "class Other {\n    int x = 10;\n}\n")
        self.f.tool("export")
        wt2 = self.f.tmp / "wt2"
        self.f.tool("setup", "--no-submodules", worktree=wt2)
        write(wt2 / "src" / "Other.java", "class Other {\n    int x = 11;\n}\n")
        self.f.tool("fixup", "feature/a", "-a", worktree=wt2)
        self.f.tool("export", worktree=wt2)
        code, out = self.f.tool("sync", check=False)
        self.assertEqual(code, 1, out)
        self.f.tool("sync", "--force")
        self.assertEqual(self.f.tool("export", "--check")[0], 0)
        self.assertIn("int x = 11", (self.f.wt / "src" / "Other.java").read_text())

    def test_setup_applies_new_tail_patches(self):
        self.setup_wt()
        self.make_patch("feature/a", "README.md", "a\n")
        self.f.tool("export")
        wt2 = self.f.tmp / "wt2"
        self.f.tool("setup", "--no-submodules", worktree=wt2)
        self.make_patch("feature/b", "src/Other.java", "class Other {\n    int x = 7;\n}\n", worktree=wt2)
        self.f.tool("export", worktree=wt2)
        _, out = self.f.tool("setup")
        self.assertIn("applied 1 patch", out)
        self.assertEqual(self.f.tool("export", "--check")[0], 0)

    def test_changed_classifies_drift(self):
        old = (b"From x\nSubject: feature/a: d\n\n---\n\ndiff --git a/f b/f\nindex 0000000..0000000 100644\n"
               b"--- a/f\n+++ b/f\n@@ -1,3 +1,3 @@\n ctx\n-old\n+new\n")
        moved = old.replace(b"@@ -1,3 +1,3 @@\n ctx", b"@@ -5,3 +5,3 @@\n other")
        changed = old.replace(b"+new", b"+newer")
        self.assertEqual(patchset.diff_signature(old), patchset.diff_signature(moved))
        self.assertNotEqual(patchset.diff_signature(old), patchset.diff_signature(changed))

    def test_todo_editor(self):
        with tempfile.TemporaryDirectory() as d:
            todo = Path(d) / "todo"
            todo.write_text("pick 1111111 a\npick 2222222 b\npick 3333333 c\n# comment\n", encoding="utf-8")
            patchset.todo_editor(["move", "3333333aaaa", "1111111bbbb", str(todo)])
            self.assertEqual(todo.read_text().splitlines()[:3], ["pick 1111111 a", "pick 3333333 c", "pick 2222222 b"])
            patchset.todo_editor(["edit", "2222222cc", str(todo)])
            self.assertIn("edit 2222222 b", todo.read_text())
            patchset.todo_editor(["drop", "1111111", str(todo)])
            self.assertNotIn("1111111", todo.read_text())

    def test_strings_lint(self):
        res = self.f.root / "fork" / "res"
        write(res / "values" / "strings_neo.xml",
              '<resources>\n    <string name="NeoGood">ok %1$s</string>\n    <string name="Bad">x</string>\n'
              '    <string name="NeoQuote">Can\'t</string>\n    <string name="NeoEscaped">Can\\\'t "fine"?</string>\n'
              '    <string name="NeoQuoted">"Can\'t"</string>\n</resources>\n')
        write(res / "values-ru" / "strings_neo.xml",
              '<resources>\n    <string name="NeoGood">локсграм</string>\n    <string name="NeoExtra">y</string>\n</resources>\n')
        code, out = self.f.tool("lint", "--fast", check=False)
        self.assertEqual(code, 1, out)
        self.assertIn("key 'Bad' must start with 'Neo'", out)
        self.assertIn("literal brand", out)
        self.assertIn("NeoExtra", out)
        self.assertIn("string 'NeoQuote' has an unescaped apostrophe", out)
        self.assertIn("string 'NeoEscaped' has an unescaped", out)  # the bare double quotes
        self.assertNotIn("string 'NeoQuoted'", out)


if __name__ == "__main__":
    unittest.main()
