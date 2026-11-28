# Bug report relay

The in-game bug reporter (`ringbearer_api`, key **F7** or **Report a bug** on the pause menu) takes a screenshot,
lets the player write a title and description, cleans the recent logs of names, IPs, tokens and user folders, and
posts it all here. This Cloudflare Worker is the only thing that holds the GitHub token: it keeps the screenshot and
logs in Workers KV (served at `https://<worker>/a/<file>`) and opens an issue on the repo, labelled `bug` and
`from-game`, linking them. Players never see a token.

Limits: 10 reports per IP per day (`DAILY_LIMIT_PER_IP`), title 120 chars, description 8000, logs 2 MB, screenshot
~9 MB. Mentions in player text are defused so reports can't ping anyone.

## Deploy (once, by the repo owner)

1. **GitHub token.** github.com > Settings > Developer settings > Fine-grained tokens > Generate. Resource owner
   `PorcupineStudios`, only the `ModernMiddleEarth_TARDIS` repository, permission **Issues: Read and write**, nothing
   else. It can't touch code. If the resource owner is your own account instead, issues still open (anyone may
   open issues on a public repo) but GitHub refuses the labels with a 403.
2. **Cloudflare.** Make a free account at dash.cloudflare.com. Then, in this folder (if PowerShell blocks `npx`,
   type `npx.cmd`):
   ```powershell
   npx wrangler login
   ```
   ```powershell
   npx wrangler kv namespace create ATTACH
   ```
   Paste the printed `id` into `wrangler.toml`.
   ```powershell
   npx wrangler secret put GITHUB_TOKEN
   ```
   Paste the token when asked.
   ```powershell
   npx wrangler deploy
   ```
   It prints the worker's address, e.g. `https://ringbearer-bugs.<you>.workers.dev`.
3. **The pack.** The address is the default of `bugReport.relayUrl` (`BugReportConfig`); change it there, or in `config/ringbearer_api-client.toml` (or in
   game: Mods > Ring Bearer API > Config).

## Admin override (skip the daily limit)

For the pack maker, who files many reports while testing. Pick a long random password, then in this folder:
```powershell
npx wrangler secret put ADMIN_KEY
```
Paste the password when asked (it takes effect at once; no redeploy needed after the first deploy that has this
code). In game: Mods > Ring Bearer API > Config > `bugReport.adminKey`, paste the same password (it's kept in your
`config/ringbearer_api-client.toml`, so never share that file or a profile export that includes it). Reports sent
with it skip the limit; a wrong key just counts against the limit as usual. To revoke it, put a new `ADMIN_KEY`.

## Working the tickets

```powershell
gh issue list --label from-game
```
Assign one to Claude (or label it) and ask a session to work it; `gh issue view <n>` gives the description, the
screenshot link and the log links.

## Test

`node test.mjs` runs the worker against an in-memory KV and a fake GitHub (no install needed).
