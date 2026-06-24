import { motion } from 'framer-motion';

export default function EnableSwitch({ enabled, busy, onToggle, title }) {
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
