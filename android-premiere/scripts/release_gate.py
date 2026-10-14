"""Fail-closed owner-wide issue check before production APK signing.

The release token must be able to see ALL repositories owned by benclawbot.
No such token is required for ordinary development/test APK builds.
"""
import json
from urllib.parse import urlencode
from urllib.request import Request, urlopen


class ReleaseBlocked(RuntimeError):
    pass


def require_no_open_issues(token, owner="benclawbot", fetch_json=None):
    """Block production signing if any owner issue is open or GitHub cannot verify."""
    if not token or not token.strip():
        raise ReleaseBlocked("A repository-wide PREMIERE_RELEASE_GITHUB_TOKEN is required for production signing")
    query = "is:issue is:open user:" + owner
    url = "https://api.github.com/search/issues?" + urlencode({"q": query, "per_page": 1})
    try:
        if fetch_json is None:
            request = Request(url, headers={
                "Authorization": "Bearer " + token.strip(),
                "Accept": "application/vnd.github+json",
                "X-GitHub-Api-Version": "2022-11-28",
                "User-Agent": "VLC-Premiere-production-release-gate",
            })
            with urlopen(request, timeout=20) as response:
                data = json.load(response)
        else:
            data = fetch_json(url)
    except Exception as exc:
        raise ReleaseBlocked("Cannot verify all open issues; production signing blocked") from exc
    if not isinstance(data, dict) or data.get("incomplete_results") is not False:
        raise ReleaseBlocked("Incomplete or invalid GitHub issue search; production signing blocked")
    count = data.get("total_count")
    if type(count) is not int or count < 0:
        raise ReleaseBlocked("GitHub issue count was missing or invalid; production signing blocked")
    if count:
        raise ReleaseBlocked(f"{count} issues remain open in {owner}-owned repositories; no production APK permitted")
    return True
