import { Hash } from 'lucide-react';
import { MAX_QUANTITY } from '../../../../shared/utils/numberInput';

/**
 * The bond quantity (adet) input. Pure presentation — `t`, the current value and the change handler are passed in.
 */
export default function QuantityField({ t, quantity, onChange }) {
  return (
    <div className="space-y-1.5">
      <label className="text-xs font-medium text-fg-muted flex items-center gap-1.5">
        <Hash className="h-3 w-3" />
        {t('portfolio.bonds.form.fields.quantity')}
      </label>
      <input
        type="number"
        step="any"
        min="0"
        max={MAX_QUANTITY}
        inputMode="decimal"
        value={quantity}
        onChange={onChange}
        placeholder={t('portfolio.bonds.form.fields.quantityPlaceholder')}
        className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2.5 text-sm text-fg font-mono placeholder:text-fg-subtle outline-none focus:ring-1 focus:ring-accent/50 transition-all"
      />
    </div>
  );
}
