import { DollarSign, Euro } from 'lucide-react';

const SIZE_CLASS = {
  xs: 'h-2.5 w-2.5',
  sm: 'h-3 w-3',
  md: 'h-3.5 w-3.5',
  lg: 'h-4 w-4',
};

const TRY_TEXT_SIZE = {
  xs: 'text-[10px]',
  sm: 'text-xs',
  md: 'text-sm',
  lg: 'text-base',
};

export default function CurrencyMarker({ code, size = 'xs', className = '' }) {
  const sz = SIZE_CLASS[size] || SIZE_CLASS.xs;
  if (code === 'USD') return <DollarSign className={`${sz} inline-block ${className}`} />;
  if (code === 'EUR') return <Euro className={`${sz} inline-block ${className}`} />;
  return (
    <span className={`${TRY_TEXT_SIZE[size] || TRY_TEXT_SIZE.xs} inline-block leading-none font-bold ${className}`}>
      ₺
    </span>
  );
}
