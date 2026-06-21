#!/usr/bin/env bash
#
# SEO / crawler smoke test.
#
# Catches the silent indexing failures that unit tests can't reach, because they
# live in nginx config + robots.txt, not in Java:
#   - the nginx $is_bot meta-refresh LOOP (a search crawler wrongly added to the
#     bot list gets bounced /aktualnosci -> /api/og -> meta-refresh -> /aktualnosci
#     forever -> GSC "Redirect error" -> pages deindexed),
#   - robots.txt fat-fingers (a stray "Disallow: /" blanks the whole site,
#     a dropped "Sitemap:" line hides the map).
#
# Safe & cheap: a handful of read-only GETs from wherever you run it (your laptop
# or a CI runner). It does NOT run in the browser, on the prod server, or inside
# the app process — zero impact on the running app, its RAM, or its speed. It
# just visits a few pages like a human would, then exits.
#
# Usage:  ./seo-smoke.sh [BASE_URL]      (default: https://nextsteppro.pl)
# Exit:   0 = all checks passed, 1 = at least one check failed.

set -uo pipefail

BASE="${1:-https://nextsteppro.pl}"
GOOGLE_UA="Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"
FB_UA="facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"
FAILED=0

pass() { echo "OK   $1"; }
fail() { echo "FAIL $1"; FAILED=1; }

get() { curl -sS -m 15 "$@"; }

echo "SEO smoke against ${BASE}"
echo "---"

# 1. robots.txt sanity -------------------------------------------------------
ROBOTS=$(get "${BASE}/robots.txt" || true)
if echo "$ROBOTS" | grep -qi "^Sitemap:"; then
  pass "robots.txt advertises a Sitemap:"
else
  fail "robots.txt is missing the Sitemap: line"
fi
# A "Disallow: /" is only dangerous when it sits under "User-agent: *" (blocks
# every crawler). A per-bot "Disallow: /" (e.g. Cloudflare's managed blocks for
# GPTBot/ClaudeBot/CCBot) is intentional and fine, so we must scope the check to
# the "*" group rather than flagging every Disallow line.
if printf '%s\n' "$ROBOTS" | awk '
    function val(s){ sub(/^[^:]*:[[:space:]]*/,"",s); gsub(/[[:space:]]/,"",s); return s }
    { line=$0; sub(/\r$/,"",line) }
    line ~ /^[[:space:]]*#/ { next }
    tolower(line) ~ /^[[:space:]]*user-agent:/ {
        if (prev_rule) star=0          # a rule before this UA means a new group starts
        if (val(line)=="*") star=1
        prev_rule=0; next
    }
    tolower(line) ~ /^[[:space:]]*disallow:/ {
        if (star==1 && val(line)=="/") blocked=1
        prev_rule=1; next
    }
    tolower(line) ~ /^[[:space:]]*allow:/ { prev_rule=1; next }
    line ~ /^[[:space:]]*$/ { prev_rule=1 }
    END { exit blocked?1:0 }
  '; then
  pass "robots.txt allows crawling under User-agent: * (per-bot AI blocks are fine)"
else
  fail "robots.txt blocks ALL crawlers (Disallow: / under User-agent: *)"
fi

# 2. sitemap.xml reachable & well-formed-ish ---------------------------------
SITEMAP=$(get "${BASE}/sitemap.xml" || true)
if echo "$SITEMAP" | grep -q "<urlset"; then
  pass "sitemap.xml served with <urlset>"
else
  fail "sitemap.xml missing or has no <urlset> root"
fi

# 3. THE bot-UA loop guard ---------------------------------------------------
# Pull a real published detail page straight from the sitemap so this stays
# correct as content changes (no hardcoded IDs).
DETAIL_URL=$(echo "$SITEMAP" | grep -oE "https?://[^<]+/(aktualnosci|kursy|team/(instruktorzy|zawodnicy))/[a-f0-9-]+" | head -1)

if [ -z "$DETAIL_URL" ]; then
  echo "WARN no published detail page found in sitemap — skipping crawler-loop check"
else
  DPATH="/$(echo "$DETAIL_URL" | cut -d/ -f4-)"
  echo "---"
  echo "Using detail page: ${DPATH}"

  # 3a. Social scraper MUST receive the server-rendered OG stub.
  FB_BODY=$(get -A "$FB_UA" "${BASE}${DPATH}" || true)
  if echo "$FB_BODY" | grep -qi 'property="og:title"'; then
    pass "social scraper (facebookexternalhit) gets the OG stub"
  else
    fail "social scraper did NOT get OG tags — link previews broken"
  fi

  # 3b. Search crawler MUST receive the SPA, never a meta-refresh.
  # A meta-refresh served to Googlebot is the exact signature of the $is_bot
  # loop that deindexes pages.
  G_BODY=$(get -A "$GOOGLE_UA" "${BASE}${DPATH}" || true)
  if echo "$G_BODY" | grep -qi 'http-equiv="refresh"'; then
    fail "search crawler got a meta-refresh — \$is_bot LOOP (GSC redirect error risk)"
  else
    pass "search crawler (Googlebot) gets the SPA, no meta-refresh loop"
  fi
fi

echo "---"
if [ "$FAILED" -ne 0 ]; then
  echo "SEO smoke FAILED — see FAIL lines above."
  exit 1
fi
echo "SEO smoke passed: robots, sitemap and crawler routing all healthy."
