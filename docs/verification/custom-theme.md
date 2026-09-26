# Custom theme saving and reference colors

Build first with `pnpm build`, then run:

```sh
node --experimental-strip-types scripts/verify-theme-save.ts
```

The browser fixture uses Playwright. Set `OMB_PLAYWRIGHT_MODULE` to its installed
module path if it is not available through normal Node resolution. Set
`OMB_THEME_EVIDENCE` to choose the evidence directory.

The launcher creates a disposable fake-engine home. The real SkinPicker is
bundled in production mode and rendered with the production, minified CSS in
a fresh headless browser context. It does not touch the installed app or data.

Checks cover the warm reference sidebar, copied/minified preset colors, saved
text and animated checkmark, success persisting without a timer, resetting on
color/preset/layout edits, reload persistence, invalid color values, and a
simulated storage failure that must show an error instead of success.

The same workflow selects Cyan GPT, checks its original cyan panel and ChatGPT
layout, reloads the selection, switches back to the warm ChatGPT preset, and
copies Cyan GPT into a custom theme. It also writes `cyan-gpt.png`.

The fixture writes `theme-saved.png` and `workflow.json`. It closes its own
browser, HTTP listener and fake-engine server on completion.

This verifies renderer behavior and storage, not an Electron installation or
a live provider conversation.
