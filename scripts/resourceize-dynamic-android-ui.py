"""Move simple interpolated Compose UI text into format string resources.

Only single-line Kotlin strings without nested string literals are handled.
Review the dry-run list and manually handle complex expressions and plurals.
"""

from hashlib import sha1
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "android/app/src/main/kotlin/com/openmausbot/companion/ui"
STRINGS = ROOT / "android/app/src/main/res/values/strings.xml"
PATTERNS = [
    re.compile(r'(\bText\(\s*)"((?:[^"\\]|\\.)*)"(?=\s*[,)]|\s*$)'),
    re.compile(r'(\b(?:text|label|header|title|placeholder|contentDescription)\s*=\s*)"((?:[^"\\]|\\.)*)"(?=\s*[,)]|\s*$)'),
]
VARIABLE = re.compile(r'\$\{([^{}]+)\}|\$([A-Za-z_]\w*)')


def xml_escape(value: str) -> str:
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'")


def convert(value: str):
    args = []

    def placeholder(match):
        args.append(match[1] or match[2])
        return f"%{len(args)}$s"

    template = VARIABLE.sub(placeholder, value)
    # A pure data label or punctuation has no language-dependent copy.
    words = re.sub(r'%\d+\$s', '', template)
    if not args or not re.search(r'[A-Za-z]{2}', words):
        return None
    return template, args


def main(apply: bool, check: bool = False):
    xml = STRINGS.read_text(encoding="utf-8")
    existing = {match[2].replace("\\'", "'").replace("&amp;", "&"): match[1]
                for match in re.finditer(r'<string name="([^"]+)"[^>]*>(.*?)</string>', xml, re.S)}
    additions = []
    changed = []
    for file in UI.glob("*.kt"):
        if file.name.endswith(("Policy.kt", "Rules.kt")):
            continue
        original = file.read_text(encoding="utf-8")
        text = original

        def replace(match):
            value = match[2]
            parsed = convert(value)
            if parsed is None:
                return match[0]
            template, args = parsed
            key = existing.get(template)
            if key is None:
                slug = re.sub(r"[^a-z0-9]+", "_", template.lower()).strip("_")[:38].rstrip("_")
                key = f"ui_dynamic_{slug}_{sha1(value.encode()).hexdigest()[:7]}"
                existing[template] = key
                additions.append((key, template))
            print(f"{file.name}: {value} -> {template} [{', '.join(args)}]")
            return f"{match[1]}stringResource(R.string.{key}, {', '.join(args)})"

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
        raise SystemExit("Unlocalized interpolated Compose text found")
    if not apply:
        return
    for file, text in changed:
        file.write_text(text, encoding="utf-8")
    if additions:
        rows = "\n".join(f'    <string name="{key}" translatable="true" formatted="false">{xml_escape(value)}</string>'
                         for key, value in additions)
        STRINGS.write_text(xml.replace("</resources>", f"{rows}\n</resources>"), encoding="utf-8")


if __name__ == "__main__":
    main("--apply" in sys.argv, "--check" in sys.argv)
