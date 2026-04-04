import {
    Bitcoin, Landmark, Building2, FileText, Coins,
    BarChart2, Newspaper,
} from 'lucide-react';

export const CATEGORY_CONFIG = {
    ALL: {
        label: 'Tümü',
        icon: Newspaper,
        color: 'text-accent',
        bg: 'bg-accent/10',
        border: 'border-accent/20',
    },
    CRYPTO: {
        label: 'Kripto',
        icon: Bitcoin,
        color: 'text-violet-400',
        bg: 'bg-violet-500/10',
        border: 'border-violet-500/20',
        fallbacks: ['/news/crypto_1.jpg', '/news/crypto_2.jpg', '/news/crypto_3.jpg'],
    },
    BORSA_ISTANBUL: {
        label: 'Borsa İstanbul',
        icon: Landmark,
        color: 'text-amber-400',
        bg: 'bg-amber-500/10',
        border: 'border-amber-500/20',
        fallbacks: ['/news/borsa_istanbul_1.jpg', '/news/borsa_istanbul_2.jpg', '/news/borsa_istanbul_3.jpg'],
    },
    BORSA_SIRKETLERI: {
        label: 'Borsa Şirketleri',
        icon: Building2,
        color: 'text-emerald-400',
        bg: 'bg-emerald-500/10',
        border: 'border-emerald-500/20',
        fallbacks: ['/news/borsa_sirketleri_1.jpg', '/news/borsa_sirketleri_2.jpg', '/news/borsa_sirketleri_3.jpg'],
    },
    TAHVIL_BONO: {
        label: 'Tahvil & Bono',
        icon: FileText,
        color: 'text-cyan-400',
        bg: 'bg-cyan-500/10',
        border: 'border-cyan-500/20',
        fallbacks: ['/news/tahvil_1.jpg', '/news/tahvil_2.jpg', '/news/tahvil_3.jpg'],
    },
    PARITE: {
        label: 'Döviz & Parite',
        icon: Coins,
        color: 'text-blue-400',
        bg: 'bg-blue-500/10',
        border: 'border-blue-500/20',
        fallbacks: ['/news/parite_1.jpg', '/news/parite_2.jpg', '/news/parite_3.jpg'],
    },
    EMTIA: {
        label: 'Emtia',
        icon: BarChart2,
        color: 'text-orange-400',
        bg: 'bg-orange-500/10',
        border: 'border-orange-500/20',
        fallbacks: ['/news/emtia_1.jpg', '/news/emtia_2.jpg', '/news/emtia_3.jpg'],
    },
    GENEL_FINANS: {
        label: 'Genel Finans',
        icon: Newspaper,
        color: 'text-fg-muted',
        bg: 'bg-surface',
        border: 'border-border-default',
        fallbacks: ['/news/genel_finans_1.jpg', '/news/genel_finans_2.jpg', '/news/genel_finans_3.jpg'],
    },
};

export const TABS = [
    'ALL',
    'CRYPTO',
    'BORSA_ISTANBUL',
    'BORSA_SIRKETLERI',
    'TAHVIL_BONO',
    'PARITE',
    'EMTIA',
    'GENEL_FINANS',
];

export const COOLDOWN_MS = 10_000;

export function getFallbackImage(category, articleId) {
    const config = CATEGORY_CONFIG[category] ?? CATEGORY_CONFIG.GENEL_FINANS;
    if (!config.fallbacks?.length) return CATEGORY_CONFIG.GENEL_FINANS.fallbacks[0];
    const idx = ((articleId ?? 0) % config.fallbacks.length + config.fallbacks.length) % config.fallbacks.length;
    return config.fallbacks[idx];
}

export function CategoryBadge({ category }) {
    const cfg = CATEGORY_CONFIG[category] ?? CATEGORY_CONFIG.GENEL_FINANS;
    const Icon = cfg.icon;
    return (
        <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[10px] font-semibold uppercase tracking-wider border ${cfg.bg} ${cfg.color} ${cfg.border}`}>
            <Icon size={10} strokeWidth={2} />
            {cfg.label}
        </span>
    );
}
