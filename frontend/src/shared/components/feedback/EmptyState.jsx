import { Inbox } from 'lucide-react';

export default function EmptyState({ icon, message, hint }) {
    return (
        <motion.div
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            className="flex flex-col items-center justify-center gap-3 rounded-2xl border border-border-default bg-bg-elevated p-14 card-hover"
        >
            <div className="flex items-center justify-center w-12 h-12 rounded-xl bg-accent/10">
                {icon || <Inbox className="h-6 w-6 text-accent" />}
            </div>
            <p className="text-sm font-medium text-fg-muted">{message}</p>
            {hint && <p className="text-xs text-fg-subtle">{hint}</p>}
        </motion.div>
    );
}
