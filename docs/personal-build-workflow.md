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

Do not install this branch until its remaining installed-only features have been ported and verified. The current installed app remains the working copy during that migration.

## Cutover checklist

The installed 0.1.83-based app was saved outside Git at `work/personal-build-backup-2026-09-24/installed-program`. The following live behaviors are not yet present in the combined 0.1.86 source and must be ported before replacing the installed app:

- Stable Codex prompt/cache routing and any associated session behavior. The installed proxy hard-codes the ChatGPT backend; a source port must retain the newer upstream provider and Company routing.
- Message selection and deletion, including the server-side scrub of stored transcripts and references and the matching UI.
- Scheduled group meetings that use a room's conversation mode and meeting allowances, as distinct from scheduled coordinator goals.
- Final provider HTTP request capture in Prompt Inspector, beyond the existing agent-input capture.

Check each port against the saved executable in an isolated fixture. Then verify a built installer without touching the live data. At cutover, fully quit the tray process and copy both user-data directories before installing. Keep the previous installer and both data copies together for rollback.
