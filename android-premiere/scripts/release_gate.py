"""Fail-closed owner-wide issue gate for FINAL publication signing.

Only classic personal access tokens with full 'repo' scope are accepted:
fine-grained/App installation tokens cannot prove access to all private repos.
"""
import json
from urllib.parse import urlencode
from urllib.request import Request, urlopen


class ReleaseBlocked(RuntimeError):
    pass


def require_no_open_issues(token, owner="benclawbot", fetch_json=None, verify_token=None):
    """Require proven owner identity, full repo scope, and complete empty issue search."""
    if not token or not token.strip():
        raise ReleaseBlocked("A full-repository PREMIERE_RELEASE_GITHUB_TOKEN is required")
    headers = {
        "Authorization": "Bearer " + token.strip(),
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "VLC-Premiere-production-release-gate",
    }

    def request(url):
        with urlopen(Request(url, headers=headers), timeout=20) as response:
            return json.load(response), response.headers

    try:
        if verify_token is None:
            identity, identity_headers = request("https://api.github.com/user")
            scopes = {s.strip() for s in identity_headers.get("X-OAuth-Scopes", "").split(",")}
            authorized = isinstance(identity, dict) and identity.get("login", "").lower() == owner.lower() and "repo" in scopes
        else:
            authorized = verify_token(owner)
        if authorized is not True:
            raise ReleaseBlocked("Token must belong to owner and have classic full repo scope")
        query = "is:issue is:open user:" + owner
        url = "https://api.github.com/search/issues?" + urlencode({"q": query, "per_page": 1})
        data = fetch_json(url) if fetch_json is not None else request(url)[0]
    except ReleaseBlocked:
        raise
    except Exception as exc:
        raise ReleaseBlocked("Cannot verify owner-wide issues; signing blocked") from exc
    if not isinstance(data, dict) or data.get("incomplete_results") is not False:
        raise ReleaseBlocked("Incomplete or invalid GitHub issue search; signing blocked")
    count = data.get("total_count")
    if type(count) is not int or count < 0:
        raise ReleaseBlocked("GitHub issue count missing or invalid; signing blocked")
    if count:
        raise ReleaseBlocked(f"{count} issues remain open in {owner}-owned repositories")
    return True
