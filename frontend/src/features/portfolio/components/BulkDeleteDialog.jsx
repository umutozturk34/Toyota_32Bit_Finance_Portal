import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { X, Trash2 } from 'lucide-react';
import { AlertTriangle, AlertCircle } from '../../../shared/components/feedback/AnimatedIcons';
import { useDeletePosition } from '../hooks/usePortfolioData';
import { useDeleteDerivativePosition } from '../hooks/useDerivativePositions';

export default function BulkDeleteDialog({ portfolioId, positions, onClose, onComplete }) {
    const { t } = useTranslation();
    const [phase, setPhase] = useState('confirm');
    const [error, setError] = useState(null);
    const spotDelete = useDeletePosition(portfolioId);
    const derivativeDelete = useDeleteDerivativePosition(portfolioId);
    const count = positions.length;
    const dismissable = phase === 'confirm';

    if (!positions || count === 0) return null;

    const handleConfirm = async () => {
        setError(null);
        setPhase('processing');
        const results = await Promise.allSettled(positions.map((p) =>
            (p.assetType === 'VIOP' ? derivativeDelete : spotDelete).mutateAsync(p.id)));
        const failed = results.filter((r) => r.status === 'rejected').length;
        if (failed > 0) {
            setError(t('portfolio.bulk.partialError', { failed, total: count }));
            setPhase('confirm');
            return;
        }
        onComplete?.(count);
        onClose();
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
                            <p className="text-sm font-semibold text-fg">{t('portfolio.bulk.confirmTitle')}</p>
                            <p className="text-xs text-fg-muted leading-relaxed">
                                <span className="font-mono font-bold text-fg tabular-nums">{count}</span> {t('portfolio.bulk.confirmBody')}
                            </p>
                        </div>
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
                            disabled={phase === 'processing'}
                            className="flex-1 rounded-lg py-2.5 text-sm font-semibold text-fg border border-border-default bg-bg-base hover:bg-surface transition-all cursor-pointer disabled:opacity-50"
                        >
                            {t('common.cancel')}
                        </button>
                        <button
                            onClick={handleConfirm}
                            disabled={phase === 'processing'}
                            className="flex-1 flex items-center justify-center gap-2 rounded-lg py-2.5 text-sm font-semibold text-white bg-danger hover:bg-danger/90 transition-all border-none cursor-pointer disabled:opacity-60"
                        >
                            <Trash2 className="h-4 w-4" />
                            {phase === 'processing' ? t('portfolio.bulk.deleting') : `${t('common.delete')} (${count})`}
                        </button>
                    </div>
                </div>
            </motion.div>
        </div>
    );
}
