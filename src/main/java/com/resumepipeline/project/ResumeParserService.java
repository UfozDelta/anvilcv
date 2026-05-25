package com.resumepipeline.project;

import com.resumepipeline.api.dto.ResumeDtos.ParsedExperience;
import com.resumepipeline.api.dto.ResumeDtos.ParsedProject;
import com.resumepipeline.api.dto.ResumeDtos.ParseResumeResponse;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class ResumeParserService {

    private static final Pattern EXPERIENCE_HEADER = Pattern.compile(
            "^(WORK\\s+EXPERIENCE|WORK\\s+HISTORY|PROFESSIONAL\\s+EXPERIENCE|" +
            "PROFESSIONAL\\s+BACKGROUND|RELEVANT\\s+EXPERIENCE|EMPLOYMENT|EXPERIENCE)\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PROJECT_HEADER = Pattern.compile(
            "^(PROJECTS?|PERSONAL\\s+PROJECTS?|SIDE\\s+PROJECTS?|SELECTED\\s+PROJECTS?|" +
            "TECHNICAL\\s+PROJECTS?|KEY\\s+PROJECTS?)\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    // Bullet line prefixes produced by PDF text extraction
    private static final Pattern BULLET_LINE = Pattern.compile(
            "^[•\\-\\*◦▪▸►✦✓·]\\s*"
    );

    // Date range pattern — month+year, year-year, year-present, bare year
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(?i)(?:" +
            "(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|" +
            "jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)" +
            "\\.?\\s+\\d{4}" +
            "|\\d{1,2}/\\d{4}" +
            "|\\d{4}\\s*[-–—]\\s*(?:\\d{4}|present|current|now)" +
            "|\\d{4}" +
            ")(?:\\s*[-–—]\\s*(?:(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|" +
            "jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\.?\\s+\\d{4}" +
            "|present|current|now|\\d{4}))?"
    );

    // Project separator: "Name -- tech stack" or "Name – tech stack"
    private static final Pattern PROJECT_SEPARATOR = Pattern.compile("\\s+[-–—]{1,2}\\s+");

    public ParseResumeResponse parse(String text) {
        String[] lines = text.split("\\r?\\n");
        Map<String, List<String>> sections = splitSections(lines);

        List<ParsedExperience> experiences = new ArrayList<>();
        List<ParsedProject> projects = new ArrayList<>();

        for (var entry : sections.entrySet()) {
            String header = entry.getKey();
            List<String> body = entry.getValue();
            if (EXPERIENCE_HEADER.matcher(header).matches()) {
                experiences.addAll(parseExperiences(body));
            } else if (PROJECT_HEADER.matcher(header).matches()) {
                projects.addAll(parseProjects(body));
            }
        }

        return new ParseResumeResponse(experiences, projects);
    }

    private Map<String, List<String>> splitSections(String[] lines) {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        String currentHeader = null;
        List<String> currentBody = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (isSectionHeader(trimmed)) {
                if (currentHeader != null) {
                    sections.put(currentHeader, new ArrayList<>(currentBody));
                }
                currentHeader = trimmed.toUpperCase();
                currentBody.clear();
            } else if (currentHeader != null) {
                currentBody.add(trimmed);
            }
        }
        if (currentHeader != null && !currentBody.isEmpty()) {
            sections.put(currentHeader, currentBody);
        }
        return sections;
    }

    private boolean isSectionHeader(String line) {
        if (line.isBlank()) return false;
        return EXPERIENCE_HEADER.matcher(line).matches() || PROJECT_HEADER.matcher(line).matches();
    }

    /**
     * A header line is any non-blank line that does NOT start with a bullet character.
     * This is the core insight: PDF text extraction preserves bullet chars but strips bold.
     * Every non-bullet line is structural (title, company, project name, dates).
     */
    private boolean isHeaderLine(String line) {
        return !line.isBlank() && !BULLET_LINE.matcher(line).find();
    }

    /**
     * Split section body into entry blocks.
     * A new entry starts at each header line that follows at least one bullet line
     * (meaning we've seen content from a prior entry), OR at the very first header line.
     *
     * Structure detected:
     *   header line(s)   ← entry metadata (title, company, dates)
     *   bullet line(s)   ← entry description
     *   header line(s)   ← next entry starts here
     *   bullet line(s)
     *   ...
     */
    private List<List<String>> splitIntoEntries(List<String> lines) {
        List<List<String>> entries = new ArrayList<>();
        List<String> current = new ArrayList<>();
        boolean seenBullet = false;

        for (String line : lines) {
            if (line.isBlank()) continue;

            if (isHeaderLine(line) && seenBullet) {
                // Bullet seen before this header → prior entry is complete
                if (!current.isEmpty()) {
                    entries.add(new ArrayList<>(current));
                    current.clear();
                }
                seenBullet = false;
            }

            current.add(line);

            if (BULLET_LINE.matcher(line).find()) {
                seenBullet = true;
            }
        }
        if (!current.isEmpty()) entries.add(current);
        return entries;
    }

    private List<ParsedExperience> parseExperiences(List<String> lines) {
        List<ParsedExperience> result = new ArrayList<>();

        for (List<String> entry : splitIntoEntries(lines)) {
            // Collect consecutive header lines at the top of the entry
            List<String> headerLines = new ArrayList<>();
            List<String> bulletLines = new ArrayList<>();
            boolean inBullets = false;

            for (String line : entry) {
                if (!inBullets && isHeaderLine(line)) {
                    headerLines.add(line);
                } else {
                    inBullets = true;
                    bulletLines.add(stripBulletPrefix(line));
                }
            }

            if (headerLines.isEmpty()) continue;

            // Line 0: "Job Title          Jan 2025 - Present"  (title + trailing date)
            // Line 1: "Company Name       City, Province"      (company + location)
            String title = null, company = null, location = null, dates = null;

            String line0 = headerLines.get(0);
            dates = extractTrailingDate(line0);
            title = dates.isBlank() ? line0 : line0.substring(0, line0.lastIndexOf(dates)).trim();

            if (headerLines.size() >= 2) {
                String line1 = headerLines.get(1);
                String line1Date = extractTrailingDate(line1);
                // line1 may also have a trailing date (some formats); strip it
                String line1Clean = line1Date.isBlank() ? line1 : line1.substring(0, line1.lastIndexOf(line1Date)).trim();
                // Split "Company  City, ON" — look for 2+ spaces or a separator
                String[] compLoc = line1Clean.split("\\s{2,}|\\t", 2);
                company = compLoc[0].trim();
                if (compLoc.length > 1) location = compLoc[1].trim();
                // Fallback: if dates not found on line0, try line1
                if (dates.isBlank() && !line1Date.isBlank()) dates = line1Date;
            }

            if (title == null || title.isBlank()) continue;

            String name = slugify((company != null ? company + "-" : "") + title);
            String description = String.join("\n", bulletLines).strip();
            result.add(new ParsedExperience(name, title, company, location, dates.isBlank() ? null : dates, description));
        }

        return result;
    }

    private List<ParsedProject> parseProjects(List<String> lines) {
        List<ParsedProject> result = new ArrayList<>();

        for (List<String> entry : splitIntoEntries(lines)) {
            List<String> bulletLines = new ArrayList<>();
            String headerLine = null;

            for (String line : entry) {
                if (headerLine == null && isHeaderLine(line)) {
                    headerLine = line;
                } else {
                    bulletLines.add(stripBulletPrefix(line));
                }
            }

            if (headerLine == null) continue;

            // Format: "ProjectName -- tech, stack, here      2025 - Present"
            // or just: "ProjectName      2025"
            String dates = extractTrailingDate(headerLine);
            String withoutDate = dates.isBlank() ? headerLine : headerLine.substring(0, headerLine.lastIndexOf(dates)).trim();

            String name;
            // Split on " -- " or " – " to get name vs tech stack
            String[] parts = PROJECT_SEPARATOR.split(withoutDate, 2);
            name = parts[0].trim();
            // parts[1] is tech stack — we don't store it separately, append to description start
            String techStack = parts.length > 1 ? parts[1].trim() : null;

            if (name.isBlank()) continue;

            String description = String.join("\n", bulletLines).strip();
            if (techStack != null && !techStack.isBlank()) {
                description = (description.isBlank() ? "" : description).strip();
            }

            result.add(new ParsedProject(name, description, dates.isBlank() ? null : dates));
        }

        return result;
    }

    /**
     * Extracts a date range from the end of a line.
     * Returns empty string if none found.
     */
    private String extractTrailingDate(String line) {
        var matcher = DATE_PATTERN.matcher(line);
        String lastMatch = "";
        int lastStart = -1;
        while (matcher.find()) {
            lastMatch = matcher.group();
            lastStart = matcher.start();
        }
        if (lastStart < 0) return "";
        // Only treat as trailing if it's in the last ~40% of the line
        if (lastStart < line.length() * 0.4) return "";
        // Expand to include range separator + second date if adjacent
        String tail = line.substring(lastStart).trim();
        return tail;
    }

    private String stripBulletPrefix(String line) {
        return BULLET_LINE.matcher(line).replaceFirst("").trim();
    }

    private String slugify(String s) {
        String slug = s.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.substring(0, Math.min(slug.length(), 60));
    }
}
