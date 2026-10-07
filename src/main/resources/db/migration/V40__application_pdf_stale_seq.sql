-- Bumped by every pdf_stale mark. A rerender/refit clears pdf_stale only if this is unchanged
-- since it loaded the row, so an edit landing mid-compile keeps the page flagged.
alter table application
    add column if not exists pdf_stale_seq bigint not null default 0;
