import i18n from '../../../shared/i18n/config';
import { CATEGORY_CONFIG } from './newsConfig';

export function CategoryBadge({ category }) {
    const cfg = CATEGORY_CONFIG[category] ?? CATEGORY_CONFIG.GENEL_FINANS;
    const Icon = cfg.icon;
    return (
        <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[10px] font-semibold uppercase tracking-wider border ${cfg.bg} ${cfg.color} ${cfg.border}`}>
            <Icon size={10} strokeWidth={2} />
            {i18n.t(cfg.labelKey)}
        </span>
    );
}
