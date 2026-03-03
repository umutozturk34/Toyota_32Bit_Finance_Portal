import { motion } from 'framer-motion';

export default function EmptyState({ icon, message, hint }) {
    return (
        <motion.div
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            className="flex flex-col items-center justify-center gap-2 rounded-lg border border-border-default bg-bg-base py-14"
        >
            {icon}
            <p className="text-sm text-fg-muted">{message}</p>
            {hint && <p className="text-xs text-fg-subtle">{hint}</p>}
        </motion.div>
    );
}
