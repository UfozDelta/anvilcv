# Adding a new project from its code

AnvilCV reads the repo itself and fills the project from what it finds.

1. **Settings › Connect GitHub** once. It installs the AnvilCV GitHub App,
   read-only, on only the repos you pick.
2. **Projects › Import from GitHub**, pick the repo. It opens on the
   project's **Repo** tab, pinned to the branch head commit.
3. Steer, optionally: star (pin) files the explorer should read first, ⊘
   (exclude) paths it must not read, pick lenses, and add notes like "I
   wrote the ingest pipeline, not the UI".
4. **Explore repo.** The first run on a commit builds a **repo map**: the
   whole repo downloaded once, modules ranked by how much the rest of the
   code depends on them, facts counted from the code (tests, endpoints,
   migrations), and summaries from modules up to subsystems, flows, and the
   project. Then a server-side agent uses the map to read the right files
   and git history, and fills the project's context fields (tech stack, role,
   ownership, scale and impact). Every claim is re-checked against what it
   actually read. Evidence citing a file it never opened is dropped, and a
   sentence quoting a number that appears nowhere in the repo is cut.
5. **Generate, then approve or reject.** On the Generate tab, pick *New
   stories* or one story, pick lenses, and run it. Wordings appear on the
   Bullets tab, where you approve, edit or trash them. Each wording shows its
   angle and, when the judge scored it, a short note on what the judge
   liked and disliked.

A field the explorer couldn't verify comes back empty rather than filled
with something plausible-sounding. That's expected, not a bug. Re-run with
notes or pins to point it at the right code.

**No GitHub access?** (Code elsewhere, or the app isn't configured on your
instance.) The context fields have no editing UI right now: the Info & Context
tab and its paste route were removed. See the known limitations in
[the reference](docs/REFERENCE.md#known-limitations).
