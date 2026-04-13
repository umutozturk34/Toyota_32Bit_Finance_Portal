import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Database, Plus, Bitcoin, TrendingUp, Briefcase, Newspaper } from 'lucide-react';
import PageHeader from '../../shared/components/PageHeader';
import ErrorState from '../../shared/components/ErrorState';
import { useAuth } from '../auth/AuthContext';
import { adminService } from './adminService';
import { toast } from '../../shared/components/Toast';
import TrackedAssetAdminPanel from './TrackedAssetAdminPanel';
import NewsSourceAdminPanel from './NewsSourceAdminPanel';

const INITIAL_FORMS = {
    CRYPTO: { assetCode: '', displayName: '', binanceSymbol: '', sortOrder: 0 },
    STOCK: { assetCode: '', displayName: '', sortOrder: 0, stockSegment: 'EQUITY' },
    FUND: { assetCode: '', displayName: '', sortOrder: 0 },
};

function TrackedAssetForm({ type, title, onSaved }) {
    const [form, setForm] = useState(INITIAL_FORMS[type]);
    const [saving, setSaving] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!form.assetCode.trim()) {
            toast.warning('Eksik Alan', 'Asset code zorunlu');
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
            toast.success('Kayıt Başarılı', `${title} kaydı eklendi/güncellendi`);
        } catch (err) {
            toast.error('İşlem Başarısız', err.response?.data?.message || err.message);
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="rounded-xl border border-border-default bg-bg-elevated card-hover backdrop-blur-md p-4">
            <h3 className="mb-3 text-sm font-semibold text-fg">{title} Ekle / Güncelle</h3>
            <form onSubmit={handleSubmit} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                <input
                    type="text"
                    placeholder={type === 'CRYPTO' ? 'Asset code (örn: bitcoin)' : 'Asset code (örn: ASELS.IS)'}
                    value={form.assetCode}
                    onChange={(e) => setForm(prev => ({ ...prev, assetCode: e.target.value }))}
                    className="rounded-lg border border-border-default bg-bg-base px-3 py-2 text-sm text-fg outline-none focus:border-accent"
                />
                <input
                    type="text"
                    placeholder="Görünen isim (opsiyonel)"
                    value={form.displayName}
                    onChange={(e) => setForm(prev => ({ ...prev, displayName: e.target.value }))}
                    className="rounded-lg border border-border-default bg-bg-base px-3 py-2 text-sm text-fg outline-none focus:border-accent"
                />
                {type === 'CRYPTO' && (
                    <input
                        type="text"
                        placeholder="Binance symbol (örn: BTCUSDT)"
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
                        placeholder="Sort"
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
                        {saving ? 'Kaydediliyor...' : 'Kaydet'}
                    </button>
                </div>
            </form>
        </div>
    );
}

const ADMIN_TABS = [
    { id: 'CRYPTO', label: 'Kripto', icon: Bitcoin },
    { id: 'STOCK', label: 'Hisse', icon: TrendingUp },
    { id: 'FUND', label: 'Fon', icon: Briefcase },
    { id: 'NEWS', label: 'Haber Kaynakları', icon: Newspaper },
];

export default function AdminTrackedAssetsPage() {
    const { hasRole } = useAuth();
    const [refreshToken, setRefreshToken] = useState(0);
    const [searchParams, setSearchParams] = useSearchParams();
    const activeTab = searchParams.get('tab') || 'CRYPTO';
    const setActiveTab = (tab) => setSearchParams(tab === 'CRYPTO' ? {} : { tab }, { replace: true });

    if (!hasRole('ADMIN')) {
        return <ErrorState message="Bu sayfayı görüntülemek için ADMIN rolü gerekli." />;
    }

    const handleChanged = () => {
        setRefreshToken(prev => prev + 1);
    };

    return (
        <div className="space-y-6 py-6">
            <PageHeader
                icon={<Database className="h-5 w-5" />}
                title="Tracked Assets Yönetimi"
                onRefresh={handleChanged}
                loading={false}
            />

            <div className="flex gap-1 rounded-xl border border-border-default bg-bg-elevated backdrop-blur-md p-1 w-fit">
                {ADMIN_TABS.map(({ id, label, icon: Icon }) => (
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
                    <TrackedAssetForm type={activeTab} title={ADMIN_TABS.find(t => t.id === activeTab)?.label} onSaved={handleChanged} />
                    <TrackedAssetAdminPanel type={activeTab} title={ADMIN_TABS.find(t => t.id === activeTab)?.label} onChanged={handleChanged} refreshToken={refreshToken} />
                </>
            )}

            {activeTab === 'NEWS' && (
                <NewsSourceAdminPanel refreshToken={refreshToken} />
            )}
        </div>
    );
}
