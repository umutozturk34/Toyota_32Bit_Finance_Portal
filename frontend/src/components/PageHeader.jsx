import { motion } from 'framer-motion';
import { RefreshCw } from 'lucide-react';
import AdminToolbar from './AdminToolbar';

export default function PageHeader({
    icon,
    title,
    onRefresh,
    loading = false,
    isAdmin = false,
    adminActions = [],
    updating = {},
    children,
}) {
    return (
        <motion.div
            initial={{ opacity: 0, y: -16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, ease: [0.16, 1, 0.3, 1] }}
            className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"
        >
            <h1 className="flex items-center gap-2.5 text-2xl font-bold tracking-[-0.025em] text-fg sm:text-3xl">
                <span className="flex items-center justify-center w-9 h-9 rounded-lg bg-accent/10 text-accent">
                    {icon}
                </span>
                {title}
            </h1>
            <div className="flex flex-wrap items-center gap-2">
                <button
                    onClick={onRefresh}
                    disabled={loading}
                    className="flex items-center gap-2 rounded-md border border-border-default bg-bg-base px-4 py-2 text-sm text-fg-muted transition-colors duration-150 hover:bg-surface hover:text-fg disabled:opacity-50"
                >
                    <RefreshCw className="h-4 w-4" />
                    Yenile
                </button>
                {isAdmin && adminActions.length > 0 && (
                    <AdminToolbar
                        actions={adminActions}
                        updating={updating}
                        disabled={loading}
                    />
                )}
                {children}
            </div>
        </motion.div>
    );
}
