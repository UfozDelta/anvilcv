/** Renders `**bold**` the way the LaTeX renderer will, so what you read is what prints. */
export function RichText({ text }: { text: string }) {
  const parts = text.split(/\*\*([\s\S]+?)\*\*/g);
  return (
    <>
      {parts.map((p, i) => (i % 2 === 1 ? <b key={i}>{p}</b> : <span key={i}>{p}</span>))}
    </>
  );
}
