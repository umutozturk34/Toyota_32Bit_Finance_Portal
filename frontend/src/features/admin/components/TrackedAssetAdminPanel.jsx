import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Reorder, useDragControls } from 'framer-motion';
import { GripVertical, Trash2, ArrowUp, ArrowDown } from 'lucide-react';
import { RefreshCw } from '../../../shared/components/feedback/AnimatedIcons';
import { adminService, trackedAssetService } from '../services/adminService';
import { toast } from '../../../shared/components/feedback/Toast';
import SearchInput from '../../../shared/components/form/SearchInput';
import Card from '../../../shared/components/card';
import ConfirmDialog from '../../../shared/components/modal/ConfirmDialog';

function ReorderItem({ item, index, total, type, onMoveUp, onMoveDown, onDelete, highlighted }) {
    const { t } = useTranslation();
    const dragControls = useDragControls();

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
                boxShadow: '0 8px 32px rgba(0,0,0,0.18)',
                zIndex: 50,
            }}
            layout
            transition={{ type: 'spring', stiffness: 350, damping: 30 }}
            className={`select-none grid grid-cols-[1fr_auto] items-center gap-3 rounded-lg border px-3 py-2 ${
                highlighted
                    ? 'border-accent bg-accent/10'
                    : 'border-border-default bg-bg-base'
            }`}
            style={{ position: 'relative' }}
        >
            <div className="min-w-0 space-y-0.5">
                <div className="flex items-center gap-2">
                    <p className="truncate text-sm font-medium text-fg">{item.assetCode}</p>
                    <span className="rounded bg-bg-elevated px-1.5 py-0.5 text-[10px] text-fg-muted">#{index + 1}</span>
                </div>
                <p className="truncate text-xs text-fg-muted">{item.displayName || item.assetCode}</p>
                {type === 'CRYPTO' && item.binanceSymbol && <p className="truncate text-xs text-fg-muted">{t('trackedAssetAdmin.binance', { symbol: item.binanceSymbol })}</p>}
            </div>

            <div className="flex items-center gap-1.5">
                <button
                    onClick={() => onMoveUp(item.assetCode)}
                    disabled={index === 0}
                    title={t('trackedAssetAdmin.moveUp')}
                    className="flex h-7 w-7 items-center justify-center rounded-md border border-border-default bg-bg-base text-fg-subtle hover:bg-surface disabled:opacity-30 transition-colors"
                >
                    <ArrowUp className="h-3.5 w-3.5" />
                </button>
                <button
                    onClick={() => onMoveDown(item.assetCode)}
                    disabled={index === total - 1}
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

export default function TrackedAssetAdminPanel({ type, title, onChanged, refreshToken = 0 }) {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const [items, setItems] = useState([]);
    const [search, setSearch] = useState('');
    const [savingOrder, setSavingOrder] = useState(false);
    const [savedSignature, setSavedSignature] = useState('');
    const [savedItems, setSavedItems] = useState([]);
    const [highlightedCode, setHighlightedCode] = useState(null);
    const [deleteTarget, setDeleteTarget] = useState(null);
    const [deleting, setDeleting] = useState(false);

    const computeSignature = useCallback((list) => {
        return (list || []).map(item => `${item.assetCode}:${item.sortOrder ?? 0}`).join('|');
    }, []);

    const { data: fetchedData, isLoading: loading } = useQuery({
        queryKey: ['trackedAssets', type, refreshToken],
        queryFn: () => trackedAssetService.getByType(type, true),
    });

    useEffect(() => {
        if (!fetchedData) return;
        const normalized = fetchedData || [];
        setItems(normalized);
        setSavedItems(normalized);
        setSavedSignature(computeSignature(normalized));
    }, [fetchedData, computeSignature]);

    const invalidate = useCallback(() => {
        queryClient.invalidateQueries({ queryKey: ['trackedAssets', type] });
    }, [queryClient, type]);

    const filteredItems = useMemo(() => {
        if (!search) return items;
        const q = search.toLowerCase();
        return items.filter(item =>
            item.assetCode.toLowerCase().includes(q) ||
            (item.displayName && item.displayName.toLowerCase().includes(q))
        );
    }, [items, search]);

    const handleDeleteClick = (item) => {
        setDeleteTarget(item);
    };

    const handleDeleteConfirm = async () => {
        if (!deleteTarget) return;
        setDeleting(true);
        try {
            await adminService.deleteTrackedAsset(type, deleteTarget.assetCode);
            setDeleteTarget(null);
            invalidate();
            onChanged?.();
            toast.success(t('trackedAssetAdmin.deleted'), t('trackedAssetAdmin.deletedBody', { code: deleteTarget.assetCode }));
        } catch (err) {
            toast.error(t('trackedAssetAdmin.deleteFailed'), err.response?.data?.message || err.message);
        } finally {
            setDeleting(false);
        }
    };

    const handleReorder = useCallback((newItems) => {
        setItems(newItems.map((entry, i) => ({ ...entry, sortOrder: i })));
    }, []);

    const savedOrderByCode = useMemo(() => {
        const map = new Map();
        for (const item of savedItems) {
            map.set(item.assetCode, item.sortOrder ?? 0);
        }
        return map;
    }, [savedItems]);

    const handleSaveOrder = async () => {
        setSavingOrder(true);
        try {
            const changedItems = items.filter((item, index) => (savedOrderByCode.get(item.assetCode) ?? index) !== index);

            if (changedItems.length > 0) {
                await adminService.updateTrackedAssetOrder({
                    assetType: type,
                    items: changedItems.map((item) => ({
                        assetCode: item.assetCode,
                        sortOrder: items.findIndex(x => x.assetCode === item.assetCode),
                    })),
                });
            }

            invalidate();
            onChanged?.();
            toast.success(t('trackedAssetAdmin.saved'), t('trackedAssetAdmin.savedBody'));
        } catch (err) {
            toast.error(t('trackedAssetAdmin.saveFailed'), err.response?.data?.message || err.message);
        } finally {
            setSavingOrder(false);
        }
    };

    const handleResetOrder = () => {
        setItems(savedItems);
    };

    const moveItemByStep = useCallback((assetCode, step) => {
        setItems(prev => {
            const index = prev.findIndex(x => x.assetCode === assetCode);
            if (index < 0) return prev;

            const nextIndex = index + step;
            if (nextIndex < 0 || nextIndex >= prev.length) return prev;

            const next = [...prev];
            const [moved] = next.splice(index, 1);
            next.splice(nextIndex, 0, moved);

            const result = next.map((entry, i) => ({ ...entry, sortOrder: i }));

            setHighlightedCode(assetCode);
            setTimeout(() => setHighlightedCode(null), 500);

            return result;
        });
    }, []);

    const hasOrderChanges = useMemo(() => computeSignature(items) !== savedSignature, [items, savedSignature, computeSignature]);

    return (
        <>
            <Card variant="elevated" backdropBlur padding="md">
                <div className="mb-3 flex items-center justify-between">
                    <h3 className="text-sm font-semibold text-fg">{t('trackedAssetAdmin.heading', { title, count: items.length, prefix: search ? `${filteredItems.length}/` : '' })}</h3>
                    <div className="flex items-center gap-2">
                        <button
                            onClick={handleResetOrder}
                            disabled={!hasOrderChanges || savingOrder}
                            className="rounded-md border border-border-default bg-bg-base px-2.5 py-1 text-xs text-fg-muted hover:bg-surface disabled:opacity-40 transition-colors"
                        >
                            {t('common.undo')}
                        </button>
                        <button
                            onClick={handleSaveOrder}
                            disabled={savingOrder || !hasOrderChanges}
                            className={`rounded-md border px-2.5 py-1 text-xs font-medium transition-colors ${
                                hasOrderChanges
                                    ? 'border-accent/50 bg-accent/10 text-accent hover:bg-accent/20'
                                    : 'border-border-default bg-bg-base text-fg-muted disabled:opacity-40'
                            }`}
                        >
                            {savingOrder ? t('trackedAssetAdmin.saving') : t('trackedAssetAdmin.saveOrder')}
                        </button>
                        <button
                            onClick={invalidate}
                            disabled={loading}
                            className="flex items-center gap-1 rounded-md border border-border-default bg-bg-base px-2.5 py-1 text-xs text-fg-muted hover:bg-surface disabled:opacity-40 transition-colors"
                        >
                            <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
                        </button>
                    </div>
                </div>

                <div className="mb-3">
                    <SearchInput value={search} onChange={setSearch} placeholder={t('trackedAssetAdmin.searchPlaceholder', { title })} />
                </div>

                {hasOrderChanges && (
                    <motion.div
                        initial={{ opacity: 0, height: 0 }}
                        animate={{ opacity: 1, height: 'auto' }}
                        exit={{ opacity: 0, height: 0 }}
                        className="mb-3 flex items-center gap-2 rounded-lg border border-warning/30 bg-warning/5 px-3 py-2 text-xs text-warning"
                    >
                        <div className="h-1.5 w-1.5 rounded-full bg-warning animate-pulse" />
                        {t('trackedAssetAdmin.unsavedChanges')}
                    </motion.div>
                )}

                {filteredItems.length === 0 ? (
                    <p className="text-xs text-fg-muted py-4 text-center">{search ? t('trackedAssetAdmin.noMatch') : t('trackedAssetAdmin.empty')}</p>
                ) : (
                    <div className="max-h-[420px] overflow-y-auto overflow-x-hidden pr-1 select-none">
                        <Reorder.Group
                            axis="y"
                            values={filteredItems}
                            onReorder={handleReorder}
                            className="space-y-2 select-none"
                            layoutScroll
                        >
                            {filteredItems.map((item, index) => (
                                <ReorderItem
                                    key={item.assetCode}
                                    item={item}
                                    index={index}
                                    total={items.length}
                                    type={type}
                                    onMoveUp={(code) => moveItemByStep(code, -1)}
                                    onMoveDown={(code) => moveItemByStep(code, 1)}
                                    onDelete={handleDeleteClick}
                                    highlighted={highlightedCode === item.assetCode}
                                />
                            ))}
                        </Reorder.Group>
                    </div>
                )}
            </Card>

            <ConfirmDialog
                open={!!deleteTarget}
                title={t('trackedAssetAdmin.deletePromptTitle', { code: deleteTarget?.assetCode ?? '' })}
                message={t('trackedAssetAdmin.deletePromptBody')}
                confirmLabel={t('common.delete')}
                cancelLabel={t('common.cancel')}
                variant="danger"
                loading={deleting}
                onConfirm={handleDeleteConfirm}
                onCancel={() => setDeleteTarget(null)}
            />
        </>
    );
}
