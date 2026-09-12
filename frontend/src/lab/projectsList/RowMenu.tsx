import { useState } from 'react';

export function RowMenu({ onDelete, onDuplicate }: { onDelete: () => void; onDuplicate: () => void }) {
  const [open, setOpen] = useState(false);
  const [confirming, setConfirming] = useState(false);
  return (
    <div className="rowmenu" onMouseLeave={() => { setOpen(false); setConfirming(false); }}>
      <button
        className="rowmenu__btn"
        aria-label="Row actions"
        aria-expanded={open}
        onClick={() => setOpen(o => !o)}
      >⋯</button>
      {open && (
        <div className="rowmenu__pop">
          <button onClick={() => { onDuplicate(); setOpen(false); }}>Duplicate</button>
          {confirming ? (
            <button className="is-danger" onClick={() => { onDelete(); setOpen(false); setConfirming(false); }}>
              Really delete?
            </button>
          ) : (
            <button className="is-danger" onClick={() => setConfirming(true)}>Delete</button>
          )}
        </div>
      )}
    </div>
  );
}
