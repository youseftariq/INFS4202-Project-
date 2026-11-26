#!/usr/bin/env bash
# Reproduces the OFF-04 defect: two students accept the last seat of a posting at the same moment.
# Expected (OFF-04): exactly one acceptance succeeds, the other is refused with an explanation.
#
# Run on a fresh start (.\start.cmd --reset), before anything else touches "QA Automation Intern" (1 seat).
# From the repository root, in Git Bash:   bash tests/repro/off-04-last-seat-race.sh
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:8080}"
PASSWORD='Uniplace!2026'
POSTING="QA Automation Intern"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

csrf() {   # csrf <jar> <path>: the CSRF token of a page
  curl -s -b "$1" -c "$1" "$BASE$2" | grep -o 'name="_csrf" value="[^"]*"' | head -1 | sed 's/.*value="//;s/"$//'
}

login() {  # login <user>: creates the cookie jar $WORK/<user>
  local jar="$WORK/$1"
  local token; token="$(csrf "$jar" /login)"
  curl -s -o /dev/null -b "$jar" -c "$jar" --data-urlencode "username=$1" --data-urlencode "password=$PASSWORD" \
       --data-urlencode "_csrf=$token" "$BASE/login"
  curl -s -b "$jar" "$BASE/dashboard" | grep -q "Log out" || { echo "Login failed for $1"; exit 1; }
}

field() {  # field <html> <name>: value of an input
  grep -o "name=\"$2\"[^>]*value=\"[^\"]*\"\|value=\"[^\"]*\"[^>]*name=\"$2\"" <<<"$1" | head -1 | grep -o 'value="[^"]*"' | sed 's/value="//;s/"$//'
}

login coordinator
login recruiter.qtech

# Usernames by full name (Coordinator -> Users)
users_html="$(curl -s -b "$WORK/coordinator" "$BASE/coordinator/users")"
username_of() { tr -d '\r' <<<"$users_html" | tr '\n' ' ' | sed 's#</tr>#\n#g' | grep "$1" | grep -o 'student\.[a-z0-9]*' | head -1; }

# The first two applications of the posting in INTERVIEW (recruiter view)
list="$(curl -s -b "$WORK/recruiter.qtech" "$BASE/applications?state=INTERVIEW" | tr -d '\r' | tr '\n' ' ' | sed 's#</tr>#\n#g' | grep "$POSTING")"
mapfile -t apps < <(grep -o 'href="/applications/[0-9]*"' <<<"$list" | grep -o '[0-9]*' | head -2)
[ "${#apps[@]}" -eq 2 ] || { echo "Need two applications in INTERVIEW for $POSTING"; exit 1; }

offers=(); students=()
for id in "${apps[@]}"; do
  page="$(curl -s -b "$WORK/recruiter.qtech" "$BASE/applications/$id")"
  name="$(grep -o "$POSTING · [^<]*" <<<"$page" | head -1 | sed "s/$POSTING · //" | sed 's/[[:space:]]*$//')"
  curl -s -o /dev/null -b "$WORK/recruiter.qtech" \
       --data-urlencode "_csrf=$(field "$page" _csrf)" \
       --data-urlencode "monthlyAllowance=$(field "$page" monthlyAllowance)" \
       --data-urlencode "weeklyHours=$(field "$page" weeklyHours)" \
       --data-urlencode "startDate=$(field "$page" startDate)" \
       --data-urlencode "endDate=$(field "$page" endDate)" \
       "$BASE/applications/$id/offer"
  user="$(username_of "$name")"
  login "$user"
  spage="$(curl -s -b "$WORK/$user" "$BASE/applications/$id")"
  offer="$(grep -o 'action="/offers/[0-9]*/accept"' <<<"$spage" | grep -o '[0-9]*')"
  echo "Application $id: $name ($user), offer $offer issued"
  offers+=("$offer"); students+=("$user")
done

# Both students accept at the same moment
for i in 0 1; do
  token="$(csrf "$WORK/${students[$i]}" "/applications/${apps[$i]}")"
  echo "$token" > "$WORK/token$i"
done
for i in 0 1; do
  curl -s -o "$WORK/accept$i.html" -b "$WORK/${students[$i]}" --data-urlencode "_csrf=$(cat "$WORK/token$i")" \
       -L "$BASE/offers/${offers[$i]}/accept" &
done
wait

text() {   # text <path>: the page as plain text on one line (coordinator view)
  curl -s -b "$WORK/coordinator" "$BASE$1" | tr -d '\r' | tr '\n' ' ' | sed 's/<[^>]*>/ /g' | tr -s ' '
}

echo
accepted=0
for i in 0 1; do
  state="$(text "/applications/${apps[$i]}" | grep -o 'QTech Solutions · [A-Za-z]*' | head -1 | sed 's/.*· //' || true)"
  echo "Application ${apps[$i]} (${students[$i]}): $state"
  [ "$state" = "Accepted" ] && accepted=$((accepted + 1))
done
posting="$(grep -o 'href="/postings/[0-9]*"' <<<"$(curl -s -b "$WORK/coordinator" "$BASE/applications/${apps[0]}")" | head -1 | grep -o '[0-9]*' || true)"
echo "Posting $POSTING: $(text "/postings/$posting" | grep -o 'Seats [0-9]* ([0-9-]* left)' | head -1 || true)"
echo
echo "Accepted: $accepted (expected by OFF-04: exactly 1)"
[ "$accepted" -eq 1 ]
