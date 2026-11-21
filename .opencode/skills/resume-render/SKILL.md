---
name: Resume Render and Page Fit
description: Calibrated LaTeX template constants, character budget, tectonic compile and page-fit trade-offs. Use before writing or editing resume.tex or compiling a PDF.
---

The calibrated one-page template is `references/resume.template.tex` (a copy of AnvilCV's
`src/main/resources/template/resume.tex`). Fill its `{{TOKEN}}` gates; do not hardcode content
into it, and do not change its layout constants casually.

## Layout constants (measured, do not change casually)

| Knob | Value |
|---|---|
| Document class | `letterpaper,10.5pt` (11pt overflows to two pages) |
| Bullet font | `\item\normalsize` = 10.5pt |
| Skills / gray lines | `\small` = 9pt |
| `\titleformat{\section}` | `\vspace{-4pt}` … `[\titlerule \vspace{-6pt}]` |
| `\resumeSubHeadingListStart` | `itemsep=7pt` |
| `\resumeItemListStart` | `itemsep=1.4pt`, `topsep=0pt` |
| Between the two Experience entries | explicit `\vspace{6pt}` |
| Before `\section{Projects}` | `\vspace{-6pt}` |
| `\resumeProjectHeading` | `\vspace{2pt}` |
| Before `\section{Technical Skills}` | `\vspace{-7pt}` |

## Character budget

A rendered line holds **~122 characters**. Target **96–104 chars for one full line** or
**170–229 chars for two**. Never write **111–163 chars** (the dead zone: line 2 ends half full),
and never exceed **240 chars** (a third line). Aim for ~70% one-line bullets.

## Skills rows

The `\skillrow` macro auto-shrinks 9 → 8 → 7pt when a row is too wide. For uniform 9pt on long
rows, bypass it:

```latex
\small\skillrowtext{Data \& Storage}{...items...}\\
```

Keep categories disjoint (languages / frameworks / data / testing / DevOps), each item once.
Avoid redundant entries ("Claude" and "Claude Code" → keep the specific tool).

## Page-fit trade-off ladder

When a change overflows to two pages, spend space in this order:

1. Shorten the text that triggered the wrap.
2. Reduce `\resumeItemListStart` itemsep by 0.5pt at a time.
3. Reduce `\resumeSubHeadingListStart` itemsep by 1–2pt.
4. Reduce the Experience `\vspace{6pt}`.
5. Reduce the section pull-ups.
6. Last resort: drop a weak bullet.

Never shrink the font below the calibrated values. Fractional-point trades work — a 1pt gap can
overflow a full page, and 0.1pt × 18 bullets buys back 1.8pt.

## Compile and verify

AnvilCV compiles through `anvil_save_application` (its `PdfCompiler` runs tectonic, bounds
concurrent compiles at 2, and has a 30s timeout). To compile locally instead:

```powershell
& 'C:\Users\F3L\bin\tectonic.exe' -X compile '<path>\resume.tex' --outdir '<path>'
```

After compiling: confirm one page, no overfull `\hbox`, no `[X]` / `LINKEDIN-HANDLE` placeholders,
and re-run the line-wrap check after any bolding change (bold glyphs are wider and can push a
two-liner to three lines — trim a trailing clause rather than dropping the emphasis).
