import { AnimatePresence, motion } from 'framer-motion';
import { Trash2, X, CheckSquare, Square } from 'lucide-react';
import { useTranslation } from 'react-i18next';

export default function BulkSelectionBar({ count, total, allSelected, onClear, onToggleAll, onDeleteClick, isDeleting }) {
    const { t } = useTranslation();
    return (
        <AnimatePresence>
            {count > 0 && (
                <motion.div
                    key="bulk-bar"
                    initial={{ y: -48, opacity: 0 }}
                    animate={{ y: 0, opacity: 1 }}
                    exit={{ y: -48, opacity: 0 }}
                    transition={{ type: 'spring', stiffness: 340, damping: 28 }}
                    className="sticky top-2 z-30 mb-2"
                >
                    <div className="flex items-center justify-between gap-2 rounded-lg border border-accent/30 bg-bg-elevated/85 backdrop-blur-md px-2.5 py-1.5 shadow-sm">
                        <div className="flex items-center gap-2 min-w-0">
                            <AnimatePresence mode="popLayout" initial={false}>
                                <motion.span
                                    key={count}
                                    initial={{ y: 4, opacity: 0 }}
                                    animate={{ y: 0, opacity: 1 }}
                                    exit={{ y: -4, opacity: 0 }}
                                    transition={{ type: 'spring', stiffness: 560, damping: 26 }}
                                    className="font-mono text-sm font-bold tabular-nums text-fg leading-none"
                                >
                                    {count}
                                </motion.span>
                            </AnimatePresence>
                            <span className="font-mono text-[10px] uppercase tracking-wider text-fg-muted whitespace-nowrap">
                                {t('portfolio.bulk.selectedSummary', { count, total })}
                            </span>
                            <button
                                type="button"
                                onClick={onToggleAll}
                                disabled={isDeleting}
                                title={allSelected ? t('portfolio.bulk.clearAll') : t('portfolio.bulk.selectAll')}
                                className="inline-flex h-6 w-6 items-center justify-center rounded text-fg-muted hover:text-fg hover:bg-bg-base/40 transition-colors cursor-pointer disabled:opacity-40"
                            >
                                {allSelected ? <CheckSquare className="h-3.5 w-3.5" /> : <Square className="h-3.5 w-3.5" />}
                            </button>
                        </div>
                        <div className="flex items-center gap-1">
                            <button
                                type="button"
                                onClick={onClear}
                                disabled={isDeleting}
                                title={t('portfolio.bulk.clear')}
                                className="inline-flex h-6 w-6 items-center justify-center rounded text-fg-muted hover:text-fg hover:bg-bg-base/40 transition-colors cursor-pointer disabled:opacity-40"
                            >
                                <X className="h-3.5 w-3.5" />
                            </button>
                            <button
                                type="button"
                                onClick={onDeleteClick}
                                disabled={isDeleting}
                                className="inline-flex items-center gap-1.5 rounded-md border border-danger/40 bg-danger/10 px-2 py-1 text-[11px] font-semibold text-danger hover:border-danger/70 hover:bg-danger/20 transition-colors cursor-pointer disabled:opacity-50"
                            >
                                <Trash2 className="h-3 w-3" />
                                <span>{t('portfolio.bulk.delete')}</span>
                                <span className="font-mono tabular-nums opacity-80">({count})</span>
                            </button>
                        </div>
                    </div>
                </motion.div>
            )}
        </AnimatePresence>
    );
}
