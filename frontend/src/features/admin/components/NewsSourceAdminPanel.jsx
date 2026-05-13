import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { AnimatePresence } from 'framer-motion';
import { Power, Trash2, Plus, Rss } from 'lucide-react';
import { RefreshCw } from '../../../shared/components/feedback/AnimatedIcons';
import { adminService } from '../services/adminService';
import { toast } from '../../../shared/components/feedback/toastBus';
import ConfirmDialog from '../../../shared/components/modal/ConfirmDialog';
import Card from '../../../shared/components/card';

const NEWS_CATEGORY_VALUES = ['', 'CRYPTO', 'BORSA_ISTANBUL', 'BORSA_SIRKETLERI', 'TAHVIL_BONO', 'PARITE', 'EMTIA', 'GENEL_FINANS'];

const INITIAL_FORM = { name: '', url: '', sourceType: 'RSS', defaultCategory: '', sortOrder: 0 };

function NewsSourceForm({ onSaved }) {
    const { t } = useTranslation();
    const [form, setForm] = useState(INITIAL_FORM);
    const [saving, setSaving] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!form.name.trim() || !form.url.trim()) {
            toast.warning(t('newsSourceAdmin.missingTitle'), t('newsSourceAdmin.missingBody'));
            return;
        }

        setSaving(true);
        try {
            await adminService.createNewsSource({
                name: form.name.trim(),
                url: form.url.trim(),
                sourceType: form.sourceType,
                defaultCategory: form.defaultCategory || null,
                sortOrder: Number(form.sortOrder) || 0,
            });
            setForm(INITIAL_FORM);
            onSaved?.();
            toast.success(t('newsSourceAdmin.added'), t('newsSourceAdmin.addedBody', { name: form.name.trim() }));
        } catch (err) {
            toast.error(t('newsSourceAdmin.addFailed'), err.response?.data?.message || err.message);
        } finally {
            setSaving(false);
        }
    };

    return (
        <Card variant="elevated" backdropBlur padding="md">
            <h3 className="mb-3 text-sm font-semibold text-fg flex items-center gap-2">
                <Rss className="h-4 w-4 text-accent" />
                {t('newsSourceAdmin.formTitle')}
            </h3>
            <form onSubmit={handleSubmit} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
                <input
                    type="text"
                    placeholder={t('newsSourceAdmin.namePlaceholder')}
                    value={form.name}
                    onChange={(e) => setForm(prev => ({ ...prev, name: e.target.value }))}
                    className="rounded-lg border border-border-default bg-bg-base px-3 py-2 text-sm text-fg outline-none focus:border-accent"
                />
                <input
                    type="text"
                    placeholder={t('newsSourceAdmin.urlPlaceholder')}
                    value={form.url}
                    onChange={(e) => setForm(prev => ({ ...prev, url: e.target.value }))}
                    className="rounded-lg border border-border-default bg-bg-base px-3 py-2 text-sm text-fg outline-none focus:border-accent lg:col-span-2"
                />
                <select
                    value={form.defaultCategory}
                    onChange={(e) => setForm(prev => ({ ...prev, defaultCategory: e.target.value }))}
                    className="rounded-lg border border-border-default bg-bg-base px-3 py-2 text-sm text-fg outline-none focus:border-accent"
                >
                    {NEWS_CATEGORY_VALUES.map(value => (
                        <option key={value || 'auto'} value={value}>
                            {value === '' ? t('newsSourceAdmin.categoryAuto') : t(`newsSourceAdmin.category.${value}`)}
                        </option>
                    ))}
                </select>
                <div className="flex items-center gap-2">
                    <input
                        type="number"
                        placeholder={t('newsSourceAdmin.orderPlaceholder')}
                        value={form.sortOrder}
                        onChange={(e) => setForm(prev => ({ ...prev, sortOrder: e.target.value }))}
                        className="w-20 rounded-lg border border-border-default bg-bg-base px-3 py-2 text-sm text-fg outline-none focus:border-accent"
                    />
                    <button
                        type="submit"
                        disabled={saving}
                        className="flex items-center gap-1 rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-white hover:bg-accent-bright disabled:opacity-60"
                    >
                        <Plus className="h-4 w-4" />
                        {saving ? t('newsSourceAdmin.adding') : t('newsSourceAdmin.addCta')}
                    </button>
                </div>
            </form>
        </Card>
    );
}

export default function NewsSourceAdminPanel({ refreshToken = 0 }) {
    const { t } = useTranslation();
    const [sources, setSources] = useState([]);
    const [loading, setLoading] = useState(false);
    const [deleteTarget, setDeleteTarget] = useState(null);
    const [deleting, setDeleting] = useState(false);

    const loadSources = useCallback(async () => {
        setLoading(true);
        try {
            const data = await adminService.getNewsSources(true);
            setSources(data || []);
        } catch {
            toast.error(t('newsSourceAdmin.errorTitle'), t('newsSourceAdmin.loadFailed'));
        } finally {
            setLoading(false);
        }
    }, [t]);

    useEffect(() => {
        loadSources();
    }, [loadSources, refreshToken]);

    const handleToggle = async (source) => {
        try {
            await adminService.setNewsSourceEnabled(source.id, !source.enabled);
            await loadSources();
        } catch (err) {
            toast.error(t('newsSourceAdmin.updateFailed'), err.response?.data?.message || err.message);
        }
    };

    const handleDeleteClick = (source) => {
        setDeleteTarget(source);
    };

    const handleDeleteConfirm = async () => {
        if (!deleteTarget) return;
        setDeleting(true);
        try {
            await adminService.deleteNewsSource(deleteTarget.id);
            setDeleteTarget(null);
            await loadSources();
            toast.success(t('newsSourceAdmin.deleted'), t('newsSourceAdmin.deletedBody', { name: deleteTarget.name }));
        } catch (err) {
            toast.error(t('newsSourceAdmin.deleteFailed'), err.response?.data?.message || err.message);
        } finally {
            setDeleting(false);
        }
    };

    return (
        <>
            <NewsSourceForm onSaved={loadSources} />

            <Card variant="elevated" backdropBlur padding="md">
                <div className="mb-3 flex items-center justify-between">
                    <h3 className="text-sm font-semibold text-fg flex items-center gap-2">
                        <Rss className="h-4 w-4 text-accent" />
                        {t('newsSourceAdmin.listTitle', { count: sources.length })}
                    </h3>
                    <button
                        onClick={loadSources}
                        disabled={loading}
                        className="flex items-center gap-1 rounded-md border border-border-default bg-bg-base px-2.5 py-1 text-xs text-fg-muted hover:bg-surface disabled:opacity-40 transition-colors"
                    >
                        <RefreshCw className={`h-3.5 w-3.5 ${loading ? 'animate-spin' : ''}`} />
                    </button>
                </div>

                {sources.length === 0 ? (
                    <p className="text-xs text-fg-muted py-4 text-center">{t('newsSourceAdmin.empty')}</p>
                ) : (
                    <div className="max-h-[420px] overflow-y-auto overflow-x-hidden pr-1">
                        <div className="space-y-2">
                            <AnimatePresence initial={false}>
                                {sources.map((source) => (
                                    <motion.div
                                        key={source.id}
                                        initial={{ opacity: 0, y: 8 }}
                                        animate={{ opacity: 1, y: 0 }}
                                        exit={{ opacity: 0, x: -20 }}
                                        transition={{ duration: 0.2 }}
                                        layout
                                        className="grid grid-cols-[1fr_auto] items-center gap-3 rounded-lg border border-border-default bg-bg-base px-3 py-2"
                                    >
                                        <div className="min-w-0 space-y-0.5">
                                            <div className="flex items-center gap-2 flex-wrap">
                                                <p className="truncate text-sm font-medium text-fg">{source.name}</p>
                                                <span className="rounded bg-bg-elevated px-1.5 py-0.5 text-[10px] text-fg-muted">
                                                    {source.sourceType}
                                                </span>
                                                {source.defaultCategory && (
                                                    <span className="rounded bg-accent/10 px-1.5 py-0.5 text-[10px] font-medium text-accent">
                                                        {source.defaultCategory}
                                                    </span>
                                                )}
                                                <span className={`rounded px-2 py-0.5 text-[10px] font-semibold ${source.enabled ? 'bg-success/15 text-success' : 'bg-danger/15 text-danger'}`}>
                                                    {source.enabled ? t('newsSourceAdmin.enabled') : t('newsSourceAdmin.disabled')}
                                                </span>
                                            </div>
                                            <p className="truncate text-xs text-fg-muted">{source.url}</p>
                                        </div>

                                        <div className="flex items-center gap-1.5">
                                            <button
                                                onClick={() => handleToggle(source)}
                                                title={source.enabled ? t('newsSourceAdmin.disable') : t('newsSourceAdmin.enable')}
                                                className={`flex h-7 w-7 items-center justify-center rounded-md border transition-colors ${
                                                    source.enabled
                                                        ? 'border-success/30 bg-success/5 text-success hover:bg-success/10'
                                                        : 'border-border-default bg-bg-base text-fg-subtle hover:bg-surface'
                                                }`}
                                            >
                                                <Power className="h-3.5 w-3.5" />
                                            </button>
                                            <button
                                                onClick={() => handleDeleteClick(source)}
                                                title={t('common.delete')}
                                                className="flex h-7 w-7 items-center justify-center rounded-md border border-danger/30 bg-danger/5 text-danger hover:bg-danger/15 transition-colors"
                                            >
                                                <Trash2 className="h-3.5 w-3.5" />
                                            </button>
                                        </div>
                                    </motion.div>
                                ))}
                            </AnimatePresence>
                        </div>
                    </div>
                )}
            </Card>

            <ConfirmDialog
                open={!!deleteTarget}
                title={t('newsSourceAdmin.deletePromptTitle', { name: deleteTarget?.name ?? '' })}
                message={t('newsSourceAdmin.deletePromptBody')}
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
