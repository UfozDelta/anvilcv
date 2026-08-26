#!/bin/sh
# Pre-warm the tectonic cache at image build time.
#
# Tectonic ships no TeX distribution: it fetches files out of a remote bundle
# one at a time, on demand. A cache miss at runtime therefore means a network
# request, and when the bundle CDN refuses the connection the compile fails
# outright ("failure requesting SHA256SUM from network") and the user gets no PDF.
#
# The previous pre-warm compiled a document whose entire body was the word
# "warm". That loads every \usepackage, but it renders one word in one font at
# one size, so it cached cmr10 and nothing else. A real resume needs \Huge and
# \scshape for the name, cmbx for the bold metrics, cmti for the italic tech
# stacks, and the math fonts behind \labelitemii's bullet. All were misses.
#
# So warm with the REAL template instead of a hand-maintained imitation of it:
# placeholders filled with dummy content, every custom macro exercised. If the
# template later gains a package or a font shape, this picks it up for free.
set -e

DIR=$(dirname "$0")
SRC="$DIR/resume.tex"
OUT=/tmp/prewarm-build
mkdir -p "$OUT"

# Section bodies. Kept as files so they can be spliced in with sed's `r`
# command -- a plain s/// would need every backslash and ampersand escaped.
cat > "$OUT/edu.frag" <<'FRAG'
\resumeSubheadingUni{University of Somewhere}{City, ST}{Honours BSc in Computer Science}{Sep 2022 -- Apr 2026}{\textbf{Coursework}: Linear Algebra, Data Structures \& Algorithms, Operating Systems}
FRAG

cat > "$OUT/exp.frag" <<'FRAG'
\resumeSubheading{Software Engineering Intern}{May 2025 -- Aug 2025}{Example Corp}{City, ST}
\resumeItemListStart
\resumeItem{Built a \textbf{Spring Boot} service processing \textbf{120K transactions/month} with \textit{idempotent} webhooks.}
\resumeItem{Cut p99 latency from \textbf{180ms to 70ms} across 3 services.}
\resumeItemListEnd
FRAG

cat > "$OUT/proj.frag" <<'FRAG'
\resumeProjectHeading{\textbf{Sample Project} $|$ \emph{Java, Spring Boot, PostgreSQL}}{2026 -- Present}
\resumeItemListStart
\resumeItem{Designed a \textbf{RAG} pipeline over \textbf{64K} records, cutting query latency under \textbf{300ms}.}
\resumeItem{Shipped a \textbf{5-role RBAC} layer covering every admin route.}
\resumeItemListEnd
FRAG

# Splice the fragments in, drop the %%SECTION%% markers the renderer consumes,
# then fill every remaining {{TOKEN}} with dummy text.
sed \
  -e '/{{EDUCATION_ITEMS}}/r '"$OUT/edu.frag" -e '/{{EDUCATION_ITEMS}}/d' \
  -e '/{{EXPERIENCE_ITEMS}}/r '"$OUT/exp.frag" -e '/{{EXPERIENCE_ITEMS}}/d' \
  -e '/{{PROJECT_ITEMS}}/r '"$OUT/proj.frag" -e '/{{PROJECT_ITEMS}}/d' \
  -e '/^%%SECTION:.*%%$/d' -e '/^%%ENDSECTION%%$/d' \
  -e 's/{{NAME}}/Firstname Lastname/g' \
  -e 's/{{PHONE}}/555-555-0100/g' \
  -e 's/{{EMAIL}}/someone@example.com/g' \
  -e 's/{{[A-Z_]*}}/sample/g' \
  "$SRC" > "$OUT/warm.tex"

# Fail loudly at build time rather than silently shipping a cold cache.
tectonic --outdir "$OUT" --chatter minimal "$OUT/warm.tex"
test -f "$OUT/warm.pdf" || { echo "prewarm: tectonic produced no PDF" >&2; exit 1; }

echo "prewarm: ok, cache now holds $(find "${XDG_CACHE_HOME:-$HOME/.cache}/Tectonic" -type f | wc -l) files"
rm -rf "$OUT"
