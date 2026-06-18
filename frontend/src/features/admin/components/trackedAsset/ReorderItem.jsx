import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { Reorder, useDragControls } from 'framer-motion';
import { GripVertical, ArrowUp, ArrowDown, Trash2 } from 'lucide-react';
import EnableSwitch from './EnableSwitch';

export default function ReorderItem({ item, rank, canMoveUp, canMoveDown, type, onMoveUp, onMoveDown, highlighted, toggling, onToggle, onDelete }) {
    const { t } = useTranslation();
    const dragControls = useDragControls();
    const enabled = item.enabled;

    return (
        <Reorder.Item
            value={item}
            dragListener={false}
            dragControls={dragControls}
            initial={{ opacity: 0, y: 8 }}
            animate={{
                opacity: 1,
                y: 0,
                scale: highlighted ? 1.01 : 1,
                transition: { duration: 0.2 },
            }}
            exit={{ opacity: 0, x: -20, transition: { duration: 0.2 } }}
            whileDrag={{
                scale: 1.02,
                boxShadow: '0 14px 38px rgba(0,0,0,0.30)',
                zIndex: 50,
            }}
            layout
            transition={{ type: 'spring', stiffness: 350, damping: 30 }}
            className={`group relative select-none grid grid-cols-[1fr_auto] items-center gap-3 rounded-lg border pl-4 pr-3 py-2 transition-colors ${
                highlighted
                    ? 'border-accent bg-accent/10'
                    : enabled
                        ? 'border-border-default bg-bg-base hover:border-border-strong'
                        : 'border-border-default/50 bg-bg-base/40'
            }`}
            style={{ position: 'relative' }}
        >
            <span
                aria-hidden
                className={`pointer-events-none absolute left-0 top-1.5 bottom-1.5 w-1 rounded-full transition-colors ${
                    enabled ? 'bg-success/70 shadow-[0_0_8px_-1px] shadow-success/60' : 'bg-fg-subtle/30'
                }`}
            />

            <div className={`min-w-0 space-y-0.5 transition-opacity ${enabled ? '' : 'opacity-55'}`}>
                <div className="flex items-center gap-2 flex-wrap">
                    <p className="truncate text-sm font-medium text-fg">{item.assetCode}</p>
                    <span className="rounded bg-bg-elevated px-1.5 py-0.5 text-[10px] text-fg-muted">#{rank}</span>
                    <span className={`rounded px-2 py-0.5 text-[10px] font-semibold tracking-wide ${
                        enabled ? 'bg-success/15 text-success' : 'bg-danger/15 text-danger'
                    }`}>
                        {enabled ? t('trackedAssetAdmin.active') : t('trackedAssetAdmin.passive')}
                    </span>
                </div>
                <p className="truncate text-xs text-fg-muted">{item.displayName || item.assetCode}</p>
                {type === 'CRYPTO' && item.binanceSymbol && <p className="truncate text-xs text-fg-muted">{t('trackedAssetAdmin.binance', { symbol: item.binanceSymbol })}</p>}
            </div>

            <div className="flex items-center gap-1.5">
                <EnableSwitch
                    enabled={enabled}
                    busy={toggling}
                    onToggle={() => onToggle(item)}
                    title={enabled ? t('trackedAssetAdmin.disable') : t('trackedAssetAdmin.enable')}
                />
                <span className="mx-0.5 h-5 w-px bg-border-default" aria-hidden />
                <button
                    onClick={() => onMoveUp(item.assetCode)}
                    disabled={!canMoveUp}
                    title={t('trackedAssetAdmin.moveUp')}
                    className="flex h-7 w-7 items-center justify-center rounded-md border border-border-default bg-bg-base text-fg-subtle hover:bg-surface disabled:opacity-30 transition-colors"
                >
                    <ArrowUp className="h-3.5 w-3.5" />
                </button>
                <button
                    onClick={() => onMoveDown(item.assetCode)}
                    disabled={!canMoveDown}
                    title={t('trackedAssetAdmin.moveDown')}
                    className="flex h-7 w-7 items-center justify-center rounded-md border border-border-default bg-bg-base text-fg-subtle hover:bg-surface disabled:opacity-30 transition-colors"
                >
                    <ArrowDown className="h-3.5 w-3.5" />
                </button>

                <motion.span
                    onPointerDown={(e) => dragControls.start(e)}
                    whileHover={{ scale: 1.1 }}
                    whileTap={{ scale: 0.95 }}
                    className="inline-flex h-7 w-7 cursor-grab items-center justify-center rounded-md border border-border-default bg-bg-base text-fg-subtle active:cursor-grabbing hover:bg-surface hover:text-fg transition-colors touch-none"
                    title={t('trackedAssetAdmin.dragHandle')}
                >
                    <GripVertical className="h-3.5 w-3.5" />
                </motion.span>

                <button
                    onClick={() => onDelete(item)}
                    title={t('common.delete')}
                    className="flex h-7 w-7 items-center justify-center rounded-md border border-danger/30 bg-danger/5 text-danger hover:bg-danger/15 transition-colors"
                >
                    <Trash2 className="h-3.5 w-3.5" />
                </button>
            </div>
        </Reorder.Item>
    );
}
