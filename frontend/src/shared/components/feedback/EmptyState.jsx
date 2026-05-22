import { Inbox } from 'lucide-react';
import { motion } from 'framer-motion';

const cx = (...parts) => parts.filter(Boolean).join(' ');

const SIZE_CONFIG = {
  sm: { wrap: 'gap-2 p-5', iconBox: 'w-9 h-9 rounded-lg', iconSize: 'h-4 w-4', title: 'text-xs', message: 'text-[11px]', hint: 'text-[10px]' },
  md: { wrap: 'gap-3 p-12', iconBox: 'w-12 h-12 rounded-xl', iconSize: 'h-5 w-5', title: 'text-sm', message: 'text-sm', hint: 'text-xs' },
  lg: { wrap: 'gap-3 p-14', iconBox: 'w-14 h-14 rounded-2xl', iconSize: 'h-6 w-6', title: 'text-base', message: 'text-sm', hint: 'text-xs' },
};

export default function EmptyState({ icon, title, message, hint, size = 'lg', action, className }) {
    const config = SIZE_CONFIG[size] ?? SIZE_CONFIG.lg;
    return (
        <motion.div
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            className={cx(
                'flex flex-col items-center justify-center text-center rounded-2xl border border-border-default bg-bg-elevated card-hover',
                config.wrap,
                className,
            )}
        >
            <div className={cx('flex items-center justify-center bg-accent/10', config.iconBox)}>
                {icon || <Inbox className={cx('text-accent', config.iconSize)} />}
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
