"""Release packaging checks using disposable credentials and archives only."""

import contextlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
import zipfile


spec = importlib.util.spec_from_file_location(
    "bundle_updater_token", Path(__file__).with_name("bundle-updater-token.py")
)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class BundleUpdaterTokenTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.jar = Path(self.directory.name) / "NeoPets.jar"
        with zipfile.ZipFile(self.jar, "w") as jar:
            jar.writestr("paper-plugin.yml", "version: '1.2.3'\n")

    def test_missing_secret_leaves_jar_unchanged_and_notices(self):
        before = self.jar.read_bytes()
        with contextlib.redirect_stdout(io.StringIO()) as output:
            module.bundle(self.jar, "")
        self.assertIn("::notice::", output.getvalue())
        self.assertEqual(before, self.jar.read_bytes())

    def test_injection_preserves_entries_and_masks_before_writing(self):
        token = "disposable-read-only-test-credential"
        with contextlib.redirect_stdout(io.StringIO()) as output:
            module.bundle(self.jar, token)
        self.assertEqual("::add-mask::" + token + "\n", output.getvalue())
        with zipfile.ZipFile(self.jar) as jar:
            self.assertEqual(b"version: '1.2.3'\n", jar.read("paper-plugin.yml"))
            self.assertEqual(token.encode(), jar.read("neopets-updater-token.txt"))
        with contextlib.redirect_stdout(io.StringIO()), self.assertRaises(SystemExit):
            module.bundle(self.jar, token)

    def test_invalid_secret_is_masked_and_rejected_without_changing_jar(self):
        before = self.jar.read_bytes()
        with contextlib.redirect_stdout(io.StringIO()) as output:
            with self.assertRaisesRegex(SystemExit, r"\[REDACTED\]"):
                module.bundle(self.jar, "disposable%token\ninvalid")
        self.assertEqual("::add-mask::disposable%25token%0Ainvalid\n", output.getvalue())
        self.assertEqual(before, self.jar.read_bytes())


if __name__ == "__main__":
    unittest.main()
