import { ArrowUp, ArrowDown } from 'lucide-react';

export default function Th({ children, align = 'left', title, sortKey, activeSort, dir, onToggle }) {
  const sortable = !!(sortKey && onToggle);
  const active = !!(sortKey && activeSort === sortKey);
  const indicator = active ? (dir === 'asc' ? <ArrowUp className="inline h-3 w-3 ml-1" /> : <ArrowDown className="inline h-3 w-3 ml-1" />) : null;
  return (
    <th
      title={title}
      onClick={sortable ? () => onToggle(sortKey) : undefined}
      className={`text-xs font-display font-semibold py-2.5 px-2 sm:px-3 select-none ${align === 'right' ? 'text-right' : 'text-left'} ${sortable ? 'cursor-pointer' : title ? 'cursor-help' : ''} ${active ? 'text-accent' : 'text-fg-muted'} ${sortable ? 'hover:text-fg' : ''}`}
    >
      {children}{indicator}
    </th>
  );
}
