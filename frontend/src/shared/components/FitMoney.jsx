import useFitChars from '../hooks/useFitChars';
import { useMoney } from '../hooks/useMoney';

// Renders a money value as fully as fits its own (parent-constrained) width, compacting — never
// CSS-clipping digits — only when the full string would overflow, so an ordinary total stays exact while
// an astronomical one collapses to ₺365,54 Tn instead of spilling outside its card. The exact value is
// always in title= (hover). The host is `block min-w-0 truncate`, so it can never overflow its container
// and the width measurement can't loop. Use for any money cell that may grow large (aggregate totals,
// hero values); for tight inline cells use useMoney().formatFit with a fixed maxChars instead.
export default function FitMoney({
  value,
  base = 'TRY',
  natural,
  dateAt,
  as: As = 'p',
  className = '',
  title,
  ...rest
}) {
  const { format, formatFit } = useMoney();
  const [ref, chars] = useFitChars();
  const text = formatFit(value, base, { natural, dateAt, maxChars: Number.isFinite(chars) ? chars : 0 });
  return (
    <As
      ref={ref}
      title={title ?? format(value, base, { natural, dateAt })}
      className={`block min-w-0 truncate ${className}`}
      {...rest}
    >
      {text}
    </As>
  );
}
