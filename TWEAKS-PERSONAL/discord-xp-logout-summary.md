# Discord XP logout summary

When a real player successfully logs in, the server posts a login notice to the dedicated login Discord webhook. This uses the same successful-login path that emits the `Player logged in ...` console entry, so it does not parse console text. When a real player logs out, a plain logout notice is sent to the status webhook; separately, the server compares their skill XP at login with their current XP and posts a total plus skill-by-skill gains to the XP webhook if they earned XP. Sessions with no XP gain still get the plain status-channel logout notice. Bot sessions are skipped.

The webhooks are optional. Configure the dedicated XP channel with PufferPanel's secret-backed `discordXpWebhook` variable (`DISCORD_XP_WEBHOOK_URL`) and login channel with `discordLoginWebhook` (`DISCORD_LOGIN_WEBHOOK_URL`). The status webhook (`discordWebhook` / `DISCORD_WEBHOOK_URL`) receives server lifecycle messages and plain player logout notices; it remains an XP fallback if no XP webhook is set. Do not store webhook secrets in `game.properties` or another tracked file. Requests are sent asynchronously, so Discord availability does not delay login, logout, or player saving.

XP totals are derived from each player's in-memory skill XP and are not used to modify saves.
