import { useTranslation } from 'react-i18next';

const TONES = {
  open: 'border-success/30 bg-success/10 text-success',
  closed: 'border-danger/30 bg-danger/10 text-danger',
};

export default function PositionStatusBadge({ closed, isDerivative = false }) {
  const { t } = useTranslation();
  const tone = closed ? TONES.closed : TONES.open;
  const labelKey = closed
    ? (isDerivative ? 'portfolio.positions.statusClosed' : 'portfolio.positions.statusSold')
    : 'portfolio.positions.statusOpen';
  return (
    <span className={`inline-flex items-center justify-center rounded-md border px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wider min-w-[56px] ${tone}`}>
      {t(labelKey)}
    </span>
  );
}
