-- True when something the PDF prints (a selected bullet, its entry heading, a profile field)
-- changed after the PDF was compiled. Set by edits, cleared by a successful rerender/refit.
alter table application
    add column if not exists pdf_stale boolean not null default false;
