import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Reorder, useDragControls } from 'framer-motion';
import { GripVertical, ArrowUp, ArrowDown, ChevronLeft, ChevronRight, Trash2 } from 'lucide-react';
import { RefreshCw } from '../../../shared/components/feedback/AnimatedIcons';
import { adminService, trackedAssetService } from '../services/adminService';
import { toast } from '../../../shared/components/feedback/toastBus';
import SearchInput from '../../../shared/components/form/SearchInput';
import Card from '../../../shared/components/card';
import ConfirmDialog from '../../../shared/components/modal/ConfirmDialog';

const PAGE_SIZE = 25;

function EnableSwitch({ enabled, busy, onToggle, title }) {
    return (
        <button
            type="button"
            role="switch"
            aria-checked={enabled}
            disabled={busy}
            onClick={onToggle}
            title={title}
            className={`relative inline-flex h-5 w-9 shrink-0 items-center rounded-full border px-0.5 transition-colors disabled:cursor-wait ${
                enabled
                    ? 'border-success/50 bg-success/25 shadow-[0_0_12px_-3px] shadow-success/60'
                    : 'border-border-default bg-bg-elevated'
            }`}
        >
            <motion.span
                className={`h-3.5 w-3.5 rounded-full ${enabled ? 'bg-success' : 'bg-fg-subtle'}`}
                animate={{ x: enabled ? 16 : 0 }}
                transition={{ type: 'spring', stiffness: 600, damping: 34 }}
            />
        </button>
    );
}

function ReorderItem({ item, rank, canMoveUp, canMoveDown, type, onMoveUp, onMoveDown, highlighted, toggling, onToggle, onDelete }) {
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

export default function TrackedAssetAdminPanel({ type, title, onChanged, refreshToken = 0 }) {
    const { t } = useTranslation();
    const queryClient = useQueryClient();
    const [items, setItems] = useState([]);
    const [search, setSearch] = useState('');
    const [savingOrder, setSavingOrder] = useState(false);
    const [savedSignature, setSavedSignature] = useState('');
    const [savedItems, setSavedItems] = useState([]);
    const [highlightedCode, setHighlightedCode] = useState(null);
    const [togglingCodes, setTogglingCodes] = useState(() => new Set());
    const [deleteTarget, setDeleteTarget] = useState(null);
    const [deleting, setDeleting] = useState(false);
    const [page, setPage] = useState(0);

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

    const pageCount = Math.max(1, Math.ceil(filteredItems.length / PAGE_SIZE));
    const safePage = Math.min(page, pageCount - 1);
    const pageItems = useMemo(
        () => filteredItems.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE),
        [filteredItems, safePage]
    );
    const indexByCode = useMemo(() => {
        const m = new Map();
        items.forEach((it, i) => m.set(it.assetCode, i));
        return m;
    }, [items]);

    const activeCount = useMemo(() => items.filter((it) => it.enabled).length, [items]);
    const passiveCount = items.length - activeCount;

    const handleReorder = useCallback((newPageItems) => {
        setItems((prev) => {
            const order = newPageItems.map((it) => it.assetCode);
            const inPage = new Set(order);
            const byCode = new Map(prev.map((it) => [it.assetCode, it]));
            const slots = [];
            prev.forEach((it, idx) => { if (inPage.has(it.assetCode)) slots.push(idx); });
            const next = [...prev];
            slots.forEach((slotIdx, k) => { next[slotIdx] = byCode.get(order[k]); });
            return next.map((entry, i) => ({ ...entry, sortOrder: i }));
        });
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

    const handleToggleEnabled = useCallback(async (item) => {
        const code = item.assetCode;
        const next = !item.enabled;
        const apply = (value) => (list) => list.map((it) => (it.assetCode === code ? { ...it, enabled: value } : it));
        setTogglingCodes((prev) => new Set(prev).add(code));
        setItems(apply(next));
        setSavedItems(apply(next));
        try {
            await adminService.setTrackedAssetEnabled(type, code, next);
            toast.success(
                t('trackedAssetAdmin.statusUpdated'),
                t(next ? 'trackedAssetAdmin.enabledBody' : 'trackedAssetAdmin.disabledBody', { code }),
            );
        } catch (err) {
            setItems(apply(item.enabled));
            setSavedItems(apply(item.enabled));
            toast.error(t('trackedAssetAdmin.statusFailed'), err.response?.data?.message || err.message);
        } finally {
            setTogglingCodes((prev) => {
                const nextSet = new Set(prev);
                nextSet.delete(code);
                return nextSet;
            });
        }
    }, [type, t]);

    const handleDeleteConfirm = async () => {
        if (!deleteTarget) return;
        const code = deleteTarget.assetCode;
        setDeleting(true);
        try {
            await adminService.deleteTrackedAsset(type, code);
            setDeleteTarget(null);
            invalidate();
            onChanged?.();
            toast.success(t('trackedAssetAdmin.deleted'), t('trackedAssetAdmin.deletedBody', { code }));
        } catch (err) {
            toast.error(t('trackedAssetAdmin.deleteFailed'), err.response?.data?.message || err.message);
        } finally {
            setDeleting(false);
        }
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
        <Card variant="elevated" backdropBlur padding="md" clip={false}>
            <div className="mb-3 flex items-start justify-between gap-2">
                <div className="min-w-0">
                    <h3 className="text-sm font-semibold text-fg">{t('trackedAssetAdmin.heading', { title, count: items.length, prefix: search ? `${filteredItems.length}/` : '' })}</h3>
                    {items.length > 0 && (
                        <p className="mt-0.5 text-[11px] text-fg-muted tabular-nums">
                            {t('trackedAssetAdmin.summary', { active: activeCount, passive: passiveCount })}
                        </p>
                    )}
                </div>
                <div className="flex items-center gap-2 shrink-0">
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
                <SearchInput value={search} onChange={(v) => { setSearch(v); setPage(0); }} placeholder={t('trackedAssetAdmin.searchPlaceholder', { title })} />
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
                <>
                    <Reorder.Group
                        axis="y"
                        values={pageItems}
                        onReorder={handleReorder}
                        className="space-y-2 select-none"
                    >
                        {pageItems.map((item) => {
                            const globalIndex = indexByCode.get(item.assetCode) ?? 0;
                            return (
                                <ReorderItem
                                    key={item.assetCode}
                                    item={item}
                                    rank={globalIndex + 1}
                                    canMoveUp={globalIndex > 0}
                                    canMoveDown={globalIndex < items.length - 1}
                                    type={type}
                                    onMoveUp={(code) => moveItemByStep(code, -1)}
                                    onMoveDown={(code) => moveItemByStep(code, 1)}
                                    highlighted={highlightedCode === item.assetCode}
                                    toggling={togglingCodes.has(item.assetCode)}
                                    onToggle={handleToggleEnabled}
                                    onDelete={setDeleteTarget}
                                />
                            );
                        })}
                    </Reorder.Group>

                    {pageCount > 1 && (
                        <div className="mt-3 flex items-center justify-between gap-2 text-xs text-fg-muted">
                            <span className="tabular-nums">
                                {safePage * PAGE_SIZE + 1}–{Math.min(filteredItems.length, (safePage + 1) * PAGE_SIZE)} / {filteredItems.length}
                            </span>
                            <div className="flex items-center gap-1.5">
                                <button
                                    type="button"
                                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                                    disabled={safePage === 0}
                                    title={t('common.previous', { defaultValue: 'Önceki' })}
                                    className="flex h-7 w-7 items-center justify-center rounded-md border border-border-default bg-bg-base text-fg-subtle hover:bg-surface disabled:opacity-30 transition-colors"
                                >
                                    <ChevronLeft className="h-3.5 w-3.5" />
                                </button>
                                <span className="tabular-nums px-1">{safePage + 1} / {pageCount}</span>
                                <button
                                    type="button"
                                    onClick={() => setPage((p) => Math.min(pageCount - 1, p + 1))}
                                    disabled={safePage >= pageCount - 1}
                                    title={t('common.next', { defaultValue: 'Sonraki' })}
                                    className="flex h-7 w-7 items-center justify-center rounded-md border border-border-default bg-bg-base text-fg-subtle hover:bg-surface disabled:opacity-30 transition-colors"
                                >
                                    <ChevronRight className="h-3.5 w-3.5" />
                                </button>
                            </div>
                        </div>
                    )}
                </>
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
