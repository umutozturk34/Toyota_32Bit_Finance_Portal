import { useState } from 'react';
import { X, Trash2, ShieldCheck } from 'lucide-react';
import { AlertTriangle, Check, AlertCircle } from '../../../shared/components/feedback/AnimatedIcons';
import ProcessingSteps from '../../../shared/components/feedback/ProcessingSteps';
import useProcessingAnimation from '../../../shared/hooks/useProcessingAnimation';
import { formatPriceTRY } from '../../../shared/utils/formatters';
import { assetCodeLabel } from '../../../shared/utils/assetCode';
import { useDeletePosition } from '../hooks/usePortfolioData';

const SUCCESS_HOLD_MS = 1100;
const PROCESSING_STEPS = [
  { label: 'İşlem doğrulanıyor...', duration: 350 },
  { label: 'Pozisyon kaldırılıyor...', duration: 400 },
  { label: 'Portföy güncelleniyor...', duration: 350 },
];

export default function PositionDeleteDialog({ portfolioId, position, onClose, onComplete }) {
  const [phase, setPhase] = useState('confirm');
  const [error, setError] = useState(null);
  const { processingStep, runAnimation, reset: resetProcessing } = useProcessingAnimation();
  const deleteMutation = useDeletePosition(portfolioId);

  if (!position) return null;
  const displayCode = assetCodeLabel(position.assetType, position.assetCode);
  const dismissable = phase === 'confirm';

  const handleConfirm = async () => {
    setError(null);
    setPhase('processing');
    try {
      await Promise.all([
        deleteMutation.mutateAsync(position.id),
        runAnimation(PROCESSING_STEPS),
      ]);
      setPhase('success');
      setTimeout(() => { onComplete?.(); onClose(); }, SUCCESS_HOLD_MS);
    } catch (err) {
      resetProcessing();
      setError(err?.response?.data?.message || 'Pozisyon silinemedi');
      setPhase('confirm');
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        className="absolute inset-0 modal-overlay backdrop-blur-sm"
        onClick={dismissable ? onClose : undefined}
      />
      <motion.div
        initial={{ opacity: 0, scale: 0.92, y: 12 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.92, y: 12 }}
        transition={{ type: 'spring', stiffness: 400, damping: 30 }}
        className="relative w-full max-w-xs rounded-2xl border border-border-default modal-panel p-5 overflow-hidden"
      >
        <div className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-danger/40 to-transparent" />

        {dismissable && (
          <button
            onClick={onClose}
            className="absolute top-3 right-3 flex items-center justify-center w-7 h-7 rounded-lg text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer"
          >
            <X className="h-3.5 w-3.5" />
          </button>
        )}

        {phase === 'success' && (
          <motion.div
            initial={{ scale: 0.85, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            className="flex flex-col items-center justify-center gap-3 py-8"
          >
            <motion.div
              initial={{ scale: 0 }}
              animate={{ scale: 1 }}
              transition={{ type: 'spring', stiffness: 300, damping: 20 }}
              className="flex items-center justify-center w-14 h-14 rounded-full bg-success/15"
            >
              <Check className="h-7 w-7 text-success" strokeWidth={2.5} />
            </motion.div>
            <motion.div
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.2 }}
              className="text-center space-y-1"
            >
              <p className="text-sm font-semibold text-fg">Pozisyon silindi</p>
              <p className="text-xs text-fg-muted">{displayCode}</p>
            </motion.div>
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              transition={{ delay: 0.35 }}
              className="flex items-center gap-1.5 text-[11px] text-success/70"
            >
              <ShieldCheck className="h-3.5 w-3.5" />
              İşlem tamamlandı
            </motion.div>
          </motion.div>
        )}

        {phase === 'processing' && <ProcessingSteps steps={PROCESSING_STEPS} currentStep={processingStep} />}

        {phase === 'confirm' && (
          <div className="space-y-4 pt-1">
            <div className="flex flex-col items-center gap-3">
              <motion.div
                initial={{ scale: 0.5, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                transition={{ type: 'spring', stiffness: 300, damping: 20, delay: 0.05 }}
                className="flex items-center justify-center w-12 h-12 rounded-full bg-danger/10"
              >
                <AlertTriangle className="h-6 w-6 text-danger" />
              </motion.div>
              <div className="text-center space-y-1">
                <p className="text-sm font-semibold text-fg">Pozisyonu Sil</p>
                <p className="text-xs text-fg-muted leading-relaxed">
                  <span className="font-medium text-fg">{displayCode}</span> pozisyonu kaldırılacak. Bu işlem geri alınamaz.
                </p>
              </div>
            </div>

            <div className="rounded-lg border border-border-default bg-bg-base px-3 py-2.5 space-y-1.5">
              <Row label="Miktar" value={Number(position.quantity).toLocaleString('tr-TR', { maximumFractionDigits: 6 })} />
              <Row label="Giriş Fiyatı" value={formatPriceTRY(position.entryPrice)} />
              {position.entryDate && (
                <Row label="Giriş Tarihi" value={new Date(position.entryDate).toLocaleDateString('tr-TR', { day: '2-digit', month: 'short', year: 'numeric' })} />
              )}
            </div>

            {error && (
              <div className="flex items-center gap-2 text-xs text-danger bg-danger/5 rounded-lg px-3 py-2 border border-danger/20">
                <AlertCircle className="h-3.5 w-3.5 shrink-0" />
                {error}
              </div>
            )}

            <div className="flex gap-2">
              <button
                onClick={onClose}
                className="flex-1 rounded-lg py-2.5 text-sm font-semibold text-fg border border-border-default bg-bg-base hover:bg-surface transition-all cursor-pointer"
              >
                Vazgeç
              </button>
              <button
                onClick={handleConfirm}
                className="flex-1 flex items-center justify-center gap-2 rounded-lg py-2.5 text-sm font-semibold text-white bg-danger hover:bg-danger/90 transition-all border-none cursor-pointer"
              >
                <Trash2 className="h-4 w-4" />
                Sil
              </button>
            </div>
          </div>
        )}
      </motion.div>
    </div>
  );
}

function Row({ label, value }) {
  return (
    <div className="flex items-center justify-between text-xs">
      <span className="text-fg-muted">{label}</span>
      <span className="font-mono font-medium text-fg">{value}</span>
    </div>
  );
}
