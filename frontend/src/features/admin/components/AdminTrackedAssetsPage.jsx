import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Database, Plus, Bitcoin, TrendingUp, Briefcase, Newspaper, Gem } from 'lucide-react';
import PageHeader from '../../../shared/components/layout/PageHeader';
import ErrorState from '../../../shared/components/feedback/ErrorState';
import { useAuth } from '../../auth/AuthContext';
import { adminService } from '../services/adminService';
import { toast } from '../../../shared/components/feedback/Toast';
import Card from '../../../shared/components/card';
import TrackedAssetAdminPanel from './TrackedAssetAdminPanel';
import NewsSourceAdminPanel from './NewsSourceAdminPanel';

const INITIAL_FORMS = {
    CRYPTO: { assetCode: '', displayName: '', binanceSymbol: '', sortOrder: 0 },
    STOCK: { assetCode: '', displayName: '', sortOrder: 0, stockSegment: 'EQUITY' },
    FUND: { assetCode: '', displayName: '', sortOrder: 0 },
    COMMODITY: { assetCode: '', displayName: '', sortOrder: 0 },
};

function TrackedAssetForm({ type, title, onSaved }) {
    const { t } = useTranslation();
    const [form, setForm] = useState(INITIAL_FORMS[type]);
    const [saving, setSaving] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!form.assetCode.trim()) {
            toast.warning(t('adminTrackedAssets.missingFieldTitle'), t('adminTrackedAssets.assetCodeRequired'));
            return;
        }

        setSaving(true);
        try {
            const normalizedCode = type === 'CRYPTO'
                ? form.assetCode.trim().toLowerCase()
                : form.assetCode.trim().toUpperCase();

            await adminService.upsertTrackedAsset({
                assetType: type,
                assetCode: normalizedCode,
                displayName: form.displayName.trim(),
                binanceSymbol: type === 'CRYPTO' ? (form.binanceSymbol?.trim().toUpperCase() || null) : null,
                enabled: true,
                stockSegment: type === 'STOCK' ? form.stockSegment : null,
                sortOrder: Number(form.sortOrder) || 0,
            });

            setForm(INITIAL_FORMS[type]);
            onSaved?.();
            toast.success(t('adminTrackedAssets.saveSuccess'), t('adminTrackedAssets.saveSuccessBody', { title }));
        } catch (err) {
            toast.error(t('error.actionFailed'), err.response?.data?.message || err.message);
        } finally {
            setSaving(false);
        }
    };

    return (
        <Card variant="elevated" backdropBlur padding="md">
            <h3 className="mb-3 text-sm font-semibold text-fg">{t('adminTrackedAssets.addOrUpdate', { title })}</h3>
            <form onSubmit={handleSubmit} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                <input
                    type="text"
                    placeholder={type === 'CRYPTO' ? t('adminTrackedAssets.assetCodeCrypto') : t('adminTrackedAssets.assetCodeOther')}
                    value={form.assetCode}
                    onChange={(e) => setForm(prev => ({ ...prev, assetCode: e.target.value }))}
                    className="rounded-lg border border-border-default bg-bg-base px-3 py-2 text-sm text-fg outline-none focus:border-accent"
                />
                <input
                    type="text"
                    placeholder={t('adminTrackedAssets.displayNamePlaceholder')}
                    value={form.displayName}
                    onChange={(e) => setForm(prev => ({ ...prev, displayName: e.target.value }))}
                    className="rounded-lg border border-border-default bg-bg-base px-3 py-2 text-sm text-fg outline-none focus:border-accent"
                />
                {type === 'CRYPTO' && (
                    <input
                        type="text"
                        placeholder={t('adminTrackedAssets.binanceSymbolPlaceholder')}
                        value={form.binanceSymbol || ''}
                        onChange={(e) => setForm(prev => ({ ...prev, binanceSymbol: e.target.value }))}
                        className="rounded-lg border border-border-default bg-bg-base px-3 py-2 text-sm text-fg outline-none focus:border-accent"
                    />
                )}
                {type === 'STOCK' ? (
                    <select
                        value={form.stockSegment}
                        onChange={(e) => setForm(prev => ({ ...prev, stockSegment: e.target.value }))}
                        className="rounded-lg border border-border-default bg-bg-base px-3 py-2 text-sm text-fg outline-none focus:border-accent"
                    >
                        <option value="EQUITY">EQUITY</option>
                        <option value="MAIN_INDEX">MAIN_INDEX</option>
                        <option value="SECONDARY_INDEX">SECONDARY_INDEX</option>
                    </select>
                ) : (
                    <input
                        type="text"
                        disabled
                        value={type}
                        className="rounded-lg border border-border-default bg-surface px-3 py-2 text-sm text-fg-muted"
                    />
                )}
                <div className="flex items-center gap-2">
                    <input
                        type="number"
                        placeholder={t('adminTrackedAssets.sortPlaceholder')}
                        value={form.sortOrder}
                        onChange={(e) => setForm(prev => ({ ...prev, sortOrder: e.target.value }))}
                        className="w-full rounded-lg border border-border-default bg-bg-base px-3 py-2 text-sm text-fg outline-none focus:border-accent"
                    />
                    <button
                        type="submit"
                        disabled={saving}
                        className="flex items-center gap-1 rounded-lg bg-accent px-4 py-2 text-sm font-semibold text-white hover:bg-accent-bright disabled:opacity-60"
                    >
                        <Plus className="h-4 w-4" />
                        {saving ? t('adminTrackedAssets.saving') : t('common.save')}
                    </button>
                </div>
            </form>
        </Card>
    );
}

const ADMIN_TAB_DEFS = [
    { id: 'CRYPTO', icon: Bitcoin },
    { id: 'STOCK', icon: TrendingUp },
    { id: 'FUND', icon: Briefcase },
    { id: 'COMMODITY', icon: Gem },
    { id: 'NEWS', icon: Newspaper },
];

export default function AdminTrackedAssetsPage() {
    const { t } = useTranslation();
    const { hasRole } = useAuth();
    const [refreshToken, setRefreshToken] = useState(0);
    const [searchParams, setSearchParams] = useSearchParams();
    const activeTab = searchParams.get('tab') || 'CRYPTO';
    const setActiveTab = (tab) => setSearchParams(tab === 'CRYPTO' ? {} : { tab }, { replace: true });
    const adminTabs = ADMIN_TAB_DEFS.map(tab => ({ ...tab, label: t(`adminTrackedAssets.tabs.${tab.id}`) }));

    if (!hasRole('ADMIN')) {
        return <ErrorState message={t('adminTrackedAssets.adminRequired')} />;
    }

    const handleChanged = () => {
        setRefreshToken(prev => prev + 1);
    };

    return (
        <div className="space-y-6 py-6">
            <PageHeader
                icon={<Database className="h-5 w-5" />}
                title={t('adminTrackedAssets.title')}
                onRefresh={handleChanged}
                loading={false}
            />

            <div className="flex gap-1 rounded-xl border border-border-default bg-bg-elevated backdrop-blur-md p-1 w-fit">
                {adminTabs.map(({ id, label, icon: Icon }) => (
                    <button
                        key={id}
                        onClick={() => setActiveTab(id)}
                        className="relative flex items-center gap-1.5 rounded-lg px-4 py-2 text-xs font-medium transition-all border-none cursor-pointer bg-transparent"
                    >
                        {activeTab === id && (
                            <motion.span
                                layoutId="admin-tab"
                                className="absolute inset-0 rounded-lg bg-accent/15"
                                transition={{ type: 'spring', stiffness: 300, damping: 30 }}
                            />
                        )}
                        <Icon className={`relative z-10 h-3.5 w-3.5 ${activeTab === id ? 'text-accent' : 'text-fg-muted'}`} />
                        <span className={`relative z-10 ${activeTab === id ? 'text-accent' : 'text-fg-muted hover:text-fg'}`}>{label}</span>
                    </button>
                ))}
            </div>

            {activeTab !== 'NEWS' && (
                <>
                    <TrackedAssetForm type={activeTab} title={adminTabs.find(tab => tab.id === activeTab)?.label} onSaved={handleChanged} />
                    <TrackedAssetAdminPanel type={activeTab} title={adminTabs.find(tab => tab.id === activeTab)?.label} onChanged={handleChanged} refreshToken={refreshToken} />
                </>
            )}

            {activeTab === 'NEWS' && (
                <NewsSourceAdminPanel refreshToken={refreshToken} />
            )}
        </div>
    );
}
