import { useMoney } from '../hooks/useMoney';

// Renders a money value in FULL — e.g. ₺70.140,00, never compacted to "₺70 B"/"₺1 Mn". The host is
// `block min-w-0 truncate`, so an over-long value clips with an ellipsis (the exact value is always in
// title= on hover) rather than spilling outside its card; every ordinary value renders completely.
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
  const { format } = useMoney();
  const text = format(value, base, { natural, dateAt });
  return (
    <As
      title={title ?? text}
      className={`block min-w-0 truncate ${className}`}
      {...rest}
    >
      {text}
    </As>
  );
}
