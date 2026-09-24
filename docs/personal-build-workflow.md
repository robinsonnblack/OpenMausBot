# Personal OpenMausBot build

`personal/release` combines the retained contribution branches on current upstream source. The contribution branches remain separate so each change can be reviewed independently. Neither chats nor credentials belong in this repository.

The Windows package uses the same application identity and data directories as the installed app. Its update feed points to releases of `robinsonnblack/OpenMausBot`, so an upstream release cannot silently replace the personal build. Publish a tested personal release with the installer and `latest.yml` before expecting in-app updates from the fork.

## Develop a change once

1. Fetch `origin/main` and create a focused feature branch from that commit. Make and test the change there.
2. Push that branch to the fork and open its separate upstream PR.
3. Merge the same feature branch into `personal/release`, resolve any integration conflict, and run the combined tests. Keep the PR branch separate.
4. Package the personal branch and install it after taking a fresh backup. There is no second implementation in the installed JavaScript.

If a feature depends on another unmerged contribution, use a stacked feature branch and state that dependency in the PR. An upstream merge should be brought into `personal/release` through the next upstream sync, not manually reapplied as a second patch.

## Update the personal app

1. Fetch and merge the current `origin/main` into `personal/release` and resolve conflicts. Check whether upstream has already merged any contribution before changing its code again.
2. Run `pnpm install --frozen-lockfile`, `pnpm typecheck`, `pnpm test`, and `pnpm package:win`. Review failures against clean upstream; never bypass required gates to package a broken integration.
3. Fully quit OpenMausBot from its system-tray menu. Back up the installed program plus `C:\Users\joshu\.openmausbot` and `C:\Users\joshu\AppData\Roaming\openmausbot`. Keep the data backup outside Git.
4. Install the generated Windows package, then verify an existing chat, a saved provider key, and the features touched by the update. If verification fails, quit the app and restore the previous program and data backup together.

## Cutover checklist

The installed 0.1.83-based app was backed up outside Git before the first personal-build installation. Codex cache routing, message deletion, final native request capture in Prompt Inspector, and scheduled group meetings have since been ported into editable source. Keep their focused tests in the package verification, alongside the full project checks above.

Before each installation, fully quit the tray process and make a fresh copy of the program and both user-data directories. Keep the previous program and data copies together for rollback.
