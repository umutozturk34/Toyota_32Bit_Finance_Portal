import i18n from '../../../shared/i18n/config';
import { CATEGORY_CONFIG } from './newsConfig';

export function CategoryBadge({ category, overlay = false }) {
    const cfg = CATEGORY_CONFIG[category] ?? CATEGORY_CONFIG.GENEL_FINANS;
    const Icon = cfg.icon;
    // `overlay` = the badge sits on top of an article image (news / featured / related cards). The theme-tinted
    // translucent chip is unreadable there — the bright image and the (light, in light mode) bottom gradient bleed
    // through the 10%-opacity background. A fixed dark glass scrim keeps the category-coloured label legible on any
    // image in BOTH light and dark mode.
    const tone = overlay
        ? `bg-black/60 ${cfg.color} border-white/15 backdrop-blur-sm shadow-sm`
        : `${cfg.bg} ${cfg.color} ${cfg.border}`;
    return (
        <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[10px] font-semibold uppercase tracking-wider border ${tone}`}>
            <Icon size={10} strokeWidth={2} />
            {i18n.t(cfg.labelKey)}
        </span>
    );
}
