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
   else. It can't touch code.
2. **Cloudflare.** Make a free account at dash.cloudflare.com. Then, in this folder:
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
3. **The pack.** Set that address as `bugReport.relayUrl` in `config/ringbearer_api-client.toml` of the pack (or in
   game: Mods > Ring Bearer API > Config), and ship that config file with the pack.

## Working the tickets

```powershell
gh issue list --label from-game
```
Assign one to Claude (or label it) and ask a session to work it; `gh issue view <n>` gives the description, the
screenshot link and the log links.

## Test

`node test.mjs` runs the worker against an in-memory KV and a fake GitHub (no install needed).
