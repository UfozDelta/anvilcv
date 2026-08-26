# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Install tectonic from GitHub releases (pinned, no install script)
# TARGETARCH is set by BuildKit; needed so this builds on Oracle A1 (aarch64) too.
ARG TARGETARCH
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates && \
    case "$TARGETARCH" in \
      arm64) TECT_ARCH=aarch64 ;; \
      amd64) TECT_ARCH=x86_64 ;; \
      *) echo "unsupported TARGETARCH: $TARGETARCH" >&2; exit 1 ;; \
    esac && \
    curl -fsSL "https://github.com/tectonic-typesetting/tectonic/releases/download/tectonic%400.15.0/tectonic-0.15.0-${TECT_ARCH}-unknown-linux-musl.tar.gz" \
      | tar -xz -C /usr/local/bin && \
    apt-get remove -y curl && apt-get autoremove -y && rm -rf /var/lib/apt/lists/*

ENV TECTONIC_BIN=/usr/local/bin/tectonic

# Pre-warm tectonic so a PDF compile never downloads anything at runtime.
#
# This used to compile a document whose body was the word "warm". That loaded
# every \usepackage but rendered one word in one font at one size, so it cached
# cmr10 and nothing else — \Huge, \scshape, bold, italic and the math fonts a
# real resume needs were all still misses. Each miss is a network fetch, and a
# refused fetch fails the compile outright, so users saw "tectonic exit 1" with
# no PDF whenever the bundle CDN blipped.
#
# prewarm.sh compiles the real template instead, so the cache cannot drift from
# what production actually renders. tr strips CRLF in case of a Windows checkout.
COPY docker/prewarm.sh /tmp/prewarm/prewarm.sh
COPY src/main/resources/template/resume.tex /tmp/prewarm/resume.tex
RUN tr -d '\r' < /tmp/prewarm/prewarm.sh > /tmp/prewarm/run.sh && \
    sh /tmp/prewarm/run.sh && \
    rm -rf /tmp/prewarm

COPY --from=build /app/target/resume-pipeline-0.1.0.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
