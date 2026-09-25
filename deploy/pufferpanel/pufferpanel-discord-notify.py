#!/usr/bin/env python3
"""Send non-blocking PufferPanel lifecycle notifications to a Discord webhook."""

from __future__ import annotations

import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request


EVENTS = {
    "starting": "is starting",
    "stopped": "process exited",
}
GAME_ID = re.compile(r"^[a-z0-9][a-z0-9-]{0,63}$")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, new_url):
        return None


def valid_webhook_url(url: str) -> bool:
    try:
        parsed = urllib.parse.urlsplit(url)
        return (
            parsed.scheme == "https"
            and parsed.hostname in {"discord.com", "discordapp.com"}
            and parsed.port in {None, 443}
            and parsed.username is None
            and parsed.password is None
            and parsed.path.startswith("/api/webhooks/")
            and bool(parsed.path.removeprefix("/api/webhooks/"))
            and not parsed.fragment
        )
    except ValueError:
        return False


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print("Usage: pufferpanel-discord-notify GAME_ID starting|stopped", file=sys.stderr)
        return 2

    game_id, event = argv[1:]
    if not GAME_ID.fullmatch(game_id) or event not in EVENTS:
        print("Invalid game ID or lifecycle event", file=sys.stderr)
        return 2

    webhook = os.environ.get("DISCORD_WEBHOOK_URL", "").strip()
    if not webhook:
        return 0
    if not valid_webhook_url(webhook):
        print("Discord notification skipped: webhook URL is not a valid Discord webhook URL", file=sys.stderr)
        return 0

    label = game_id.replace("-", " ").upper() if game_id == "void-rsps" else game_id.replace("-", " ").title()
    payload = json.dumps({"content": f"**{label}** {EVENTS[event]}."}, ensure_ascii=True).encode("utf-8")
    request = urllib.request.Request(
        webhook,
        data=payload,
        headers={"Content-Type": "application/json", "User-Agent": "PufferPanel-Discord-Notifier/1.0"},
        method="POST",
    )
    opener = urllib.request.build_opener(NoRedirect)

    try:
        with opener.open(request, timeout=8) as response:
            if not 200 <= response.status < 300:
                print(f"Discord notification failed with HTTP status {response.status}", file=sys.stderr)
    except urllib.error.HTTPError as error:
        print(f"Discord notification failed with HTTP status {error.code}", file=sys.stderr)
    except (urllib.error.URLError, TimeoutError, OSError) as error:
        # URL errors may contain the webhook token; deliberately omit details.
        print(f"Discord notification failed ({type(error).__name__})", file=sys.stderr)

    # Notification delivery must not prevent a game server from starting or stopping.
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
