import { ArrowUp, ArrowDown } from 'lucide-react';

export default function ReturnsTh({ children, align = 'left', title, active = false, dir }) {
  return (
    <th
      title={title}
      className={`text-xs font-display font-semibold py-2.5 px-2 sm:px-3 select-none whitespace-nowrap ${align === 'right' ? 'text-right' : 'text-left'} ${title ? 'cursor-help' : ''} ${active ? 'text-accent' : 'text-fg-muted'}`}
    >
      {children}
      {active && (dir === 'asc' ? <ArrowUp className="inline h-3 w-3 ml-1" /> : <ArrowDown className="inline h-3 w-3 ml-1" />)}
    </th>
  );
}
