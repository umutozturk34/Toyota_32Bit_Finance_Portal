import i18n from '../../../shared/i18n/config';
import { CATEGORY_CONFIG } from './newsConfig';

export function CategoryBadge({ category, overlay = false }) {
    const cfg = CATEGORY_CONFIG[category] ?? CATEGORY_CONFIG.GENEL_FINANS;
    const Icon = cfg.icon;
    // `overlay` = the badge sits on top of an article image (news / featured / related cards). The theme-tinted
    // translucent chip is unreadable there — the bright image and the (light, in light mode) bottom gradient bleed
    // through the 10%-opacity background. A fixed dark glass scrim keeps the category-coloured label legible on any
    // image in BOTH light and dark mode.
    // On an image, the category's own theme-tinted colour can wash out (e.g. text-accent / text-fg-muted go dark
    // in light mode) — so the overlay uses a strong dark scrim plus a FIXED-BRIGHT category colour (overlayColor),
    // keeping the category identity legible AND colourful on any image in both themes.
    const tone = overlay
        ? `bg-black/70 ${cfg.overlayColor ?? 'text-white'} border-white/25 backdrop-blur-sm shadow-md`
        : `${cfg.bg} ${cfg.color} ${cfg.border}`;
    return (
        <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[10px] font-semibold uppercase tracking-wider border ${tone}`}>
            <Icon size={10} strokeWidth={2} />
            {i18n.t(cfg.labelKey)}
        </span>
    );
}
