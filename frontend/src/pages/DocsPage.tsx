export function DocsPage() {
  return (
    <div className="shell" style={{ paddingTop: 48, paddingBottom: 80 }}>
      <div style={{ borderBottom: '3px solid var(--ink)', paddingBottom: 12, marginBottom: 36 }}>
        <span style={{ fontFamily: 'var(--mono)', fontWeight: 700, fontSize: 11, letterSpacing: '0.22em', textTransform: 'uppercase' }}>
          06 — DOCS
        </span>
      </div>
      <p style={{ fontFamily: 'var(--mono)', fontSize: 13, color: 'var(--muted)', lineHeight: 1.7 }}>
        API documentation coming soon.
      </p>
    </div>
  );
}
