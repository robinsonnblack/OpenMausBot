"""Move simple Compose UI literals into Android string resources.

Run with --apply after inspecting the printed candidates. Interpolated or
concatenated strings need a separate, manually reviewed conversion.
"""

from hashlib import sha1
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "android/app/src/main/kotlin/com/openmausbot/companion/ui"
STRINGS = ROOT / "android/app/src/main/res/values/strings.xml"
SKIP_FILES = {"ProfileRules.kt"}  # Values constructed outside composition.
SKIP_TEXT = {"/", "OpenMausBot"}
PATTERNS = [
    re.compile(r'(\bText\(\s*)"([^"$\\]+)"(?=\s*\))'),
    re.compile(r'(\b(?:text|label|header|title|placeholder|contentDescription)\s*=\s*)"([^"$\\]+)"(?=\s*[,)]|\s*$)', re.M),
]


def xml_escape(value: str) -> str:
    return (value.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("'", "\\'"))


def main(apply: bool, check: bool = False) -> None:
    xml = STRINGS.read_text(encoding="utf-8")
    existing = {}
    for match in re.finditer(r'<string name="([^"]+)"[^>]*>(.*?)</string>', xml, re.S):
        existing.setdefault(match[2].replace("\\'", "'").replace("&amp;", "&"), match[1])
    additions = []
    changed = []
    for file in UI.glob("*.kt"):
        if file.name in SKIP_FILES or file.name.endswith(("Policy.kt", "Rules.kt")):
            continue
        original = file.read_text(encoding="utf-8")
        text = original

        def replace(match: re.Match) -> str:
            value = match[2]
            if value in SKIP_TEXT or value.isnumeric():
                return match[0]
            key = existing.get(value)
            if key is None:
                slug = re.sub(r"[^a-z0-9]+", "_", value.lower()).strip("_")[:43].rstrip("_")
                key = f"ui_{slug}_{sha1(value.encode()).hexdigest()[:7]}"
                existing[value] = key
                additions.append((key, value))
            print(f"{file.name}: {value} -> {key}")
            return f"{match[1]}stringResource(R.string.{key})"

        for pattern in PATTERNS:
            text = pattern.sub(replace, text)
        if text == original:
            continue
        if "import androidx.compose.ui.res.stringResource" not in text:
            text = text.replace("\nimport ", "\nimport androidx.compose.ui.res.stringResource\nimport ", 1)
        if "import com.openmausbot.companion.R" not in text:
            text = text.replace("\nimport ", "\nimport com.openmausbot.companion.R\nimport ", 1)
        changed.append((file, text))

    print(f"Files: {len(changed)}; new English resources: {len(additions)}")
    if check and changed:
        raise SystemExit("Unlocalized static Compose text found")
    if not apply:
        print("Dry run. Use --apply after reviewing the candidates.")
        return
    for file, text in changed:
        file.write_text(text, encoding="utf-8")
    if additions:
        rows = "\n".join(f'    <string name="{key}" translatable="true" formatted="false">{xml_escape(value)}</string>'
                         for key, value in additions)
        xml = xml.replace("</resources>", f"{rows}\n</resources>")
        STRINGS.write_text(xml, encoding="utf-8")


if __name__ == "__main__":
    main("--apply" in sys.argv, "--check" in sys.argv)
