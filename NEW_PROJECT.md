# Adding a new project from its code

AnvilCV reads the repo itself. The old "copy the extractor prompt into a
coding agent and paste the JSON back" flow is retired.

1. **Settings › Connect GitHub** once. It installs the AnvilCV GitHub App,
   read-only, on only the repos you pick.
2. **Projects › Import from GitHub**, pick the repo. It opens on the
   project's **Repo** tab, pinned to the branch head commit.
3. Steer, optionally: star (pin) files the explorer should read first, ⊘
   (exclude) paths it must not read, pick lenses, and add notes like "I
   wrote the ingest pipeline, not the UI".
4. **Explore repo.** A server-side agent walks the tree, reads files and
   git history, and fills Info & Context. Every claim is re-checked
   against what it actually read. Evidence citing a file it never opened is
   dropped, and a sentence quoting a number that appears nowhere in the
   repo is cut.
5. **Generate bank**, then approve or reject. Each bullet shows the files
   and commits it traces to. Click one to open the cited lines.

A field the explorer couldn't verify comes back empty rather than filled
with something plausible-sounding. That's expected, not a bug. Re-run with
notes or pins to point it at the right code.

**No GitHub access?** (Code elsewhere, or the app isn't configured on your
instance.) Run the extractor from
[anvilcv-context-mcp](../anvilcv-context-mcp) in your own coding agent and
paste its JSON into **Info & Context › Architecture & Context › paste**.
