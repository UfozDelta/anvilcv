interface SectionProps {
  num: string;
  title: string;
  count?: string | number;
  children?: React.ReactNode;
}

export function Section({ num, title, count }: SectionProps) {
  return (
    <div className="section-mark">
      <div className="section-mark__num">{num}</div>
      <div className="section-mark__title">{title}</div>
      <div className="section-mark__count">{count !== undefined ? `(${count})` : ''}</div>
    </div>
  );
}
