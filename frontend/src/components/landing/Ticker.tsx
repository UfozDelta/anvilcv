/** Endless acid marquee. The list is rendered twice so the -50% loop is seamless. */
export function Ticker({ items }: { items: string[] }) {
  if (!items.length) return null;
  return (
    <div className="hx-ticker" aria-label="Roles in the job feed">
      <div className="hx-ticker__track">
        {[...items, ...items].map((r, i) => (
          <span key={i} className="hx-ticker__item" aria-hidden={i >= items.length || undefined}>
            {r.toUpperCase()} <span className="lp-muted">✦</span>
          </span>
        ))}
      </div>
    </div>
  );
}
