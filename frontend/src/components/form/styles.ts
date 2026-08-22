export const formStyles = {
  section: {
    padding: '20px 0 8px',
    marginBottom: 8,
  },
  row: {
    display: 'flex' as const,
    alignItems: 'center' as const,
    gap: 16,
    marginBottom: 20,
  },
  label: {
    fontFamily: 'var(--mono)',
    fontSize: '0.78rem',
    color: 'var(--ink-2)',
    minWidth: 140,
  },
  toggleRow: {
    display: 'flex' as const,
    alignItems: 'center' as const,
    fontFamily: 'var(--mono)',
    fontSize: '0.78rem',
    marginBottom: 20,
    cursor: 'pointer',
  },
  sliderRow: {
    display: 'flex' as const,
    alignItems: 'center' as const,
    gap: 8,
  },
  slider: {
    flex: 1,
    accentColor: 'var(--ink)',
  },
  sliderCap: {
    fontFamily: 'var(--mono)',
    fontSize: '0.68rem',
    color: 'var(--ink-3)',
    width: 24,
    textAlign: 'center' as const,
  },
  sliderValue: {
    fontFamily: 'var(--mono)',
    fontSize: '0.68rem',
    color: 'var(--ink-2)',
    width: 60,
  },
};
