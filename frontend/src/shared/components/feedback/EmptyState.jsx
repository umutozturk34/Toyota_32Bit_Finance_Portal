import { Inbox, SearchX, Lock, PackageOpen } from 'lucide-react';
import { motion } from 'framer-motion';

const cx = (...parts) => parts.filter(Boolean).join(' ');

const SIZE_CONFIG = {
    sm: { wrap: 'gap-2 p-5', iconBox: 'w-9 h-9 rounded-lg', iconSize: 'h-4 w-4', title: 'text-xs', message: 'text-[11px]', hint: 'text-[10px]' },
    md: { wrap: 'gap-3 p-12', iconBox: 'w-12 h-12 rounded-xl', iconSize: 'h-5 w-5', title: 'text-sm', message: 'text-sm', hint: 'text-xs' },
    lg: { wrap: 'gap-3 p-14', iconBox: 'w-14 h-14 rounded-2xl', iconSize: 'h-6 w-6', title: 'text-base', message: 'text-sm', hint: 'text-xs' },
};

// Each variant carries its own icon + tone so a "no search results" reads differently from "no access" or a
// genuinely-empty workspace — instead of every empty state looking like the same grey Inbox. An explicit `icon`
// prop still overrides. Tone drives the icon-box tint and its soft backdrop glow.
const VARIANT_CONFIG = {
    default: { Icon: Inbox, tone: 'accent' },
    noresults: { Icon: SearchX, tone: 'subtle' },
    nopermission: { Icon: Lock, tone: 'warning' },
    empty: { Icon: PackageOpen, tone: 'accent' },
};

const TONE = {
    accent: { box: 'bg-accent/10 text-accent', glow: 'bg-accent/20' },
    subtle: { box: 'bg-surface text-fg-subtle', glow: 'bg-fg-subtle/10' },
    warning: { box: 'bg-warning/10 text-warning', glow: 'bg-warning/20' },
};

export default function EmptyState({ icon, title, message, hint, size = 'lg', variant = 'default', action, className }) {
    const config = SIZE_CONFIG[size] ?? SIZE_CONFIG.lg;
    const v = VARIANT_CONFIG[variant] ?? VARIANT_CONFIG.default;
    const tone = TONE[v.tone] ?? TONE.accent;
    const Icon = v.Icon;
    return (
        <motion.div
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}
            className={cx(
                'flex flex-col items-center justify-center text-center rounded-2xl border border-border-default bg-bg-elevated card-hover',
                config.wrap,
                className,
            )}
        >
            <div className="relative">
                {/* soft halo behind the glyph for depth — pulses gently so the empty state feels alive, not dead */}
                <motion.span
                    aria-hidden="true"
                    className={cx('absolute inset-0 rounded-full blur-xl', tone.glow)}
                    animate={{ opacity: [0.4, 0.75, 0.4], scale: [0.9, 1.05, 0.9] }}
                    transition={{ duration: 3.2, repeat: Infinity, ease: 'easeInOut' }}
                />
                <motion.div
                    className={cx('relative flex items-center justify-center', config.iconBox, tone.box)}
                    animate={{ y: [0, -3, 0] }}
                    transition={{ duration: 3.2, repeat: Infinity, ease: 'easeInOut' }}
                >
                    {icon || <Icon className={config.iconSize} />}
                </motion.div>
            </div>
            {title && <p className={cx('font-semibold text-fg', config.title)}>{title}</p>}
            <p className={cx('font-medium text-fg-muted', config.message)}>{message}</p>
            {hint && <p className={cx('text-fg-subtle', config.hint)}>{hint}</p>}
            {action && (
                <button
                    type="button"
                    onClick={action.onClick}
                    className="mt-2 inline-flex items-center gap-1.5 text-xs font-semibold text-accent hover:text-accent-bright transition-colors bg-transparent border-none cursor-pointer"
                >
                    {action.icon && <action.icon className="h-3.5 w-3.5" />}
                    {action.label}
                </button>
            )}
        </motion.div>
    );
}
