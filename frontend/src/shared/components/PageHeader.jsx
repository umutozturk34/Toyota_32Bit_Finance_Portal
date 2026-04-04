import { useState, useEffect, useCallback } from 'react';
import { motion } from 'framer-motion';
import { RefreshCw, Clock } from 'lucide-react';
import AdminToolbar from './AdminToolbar';

const COOLDOWN_MS = 10 * 1000;

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
    const [cooldownEnd, setCooldownEnd] = useState(null);
    const [remaining, setRemaining] = useState(0);

    useEffect(() => {
        if (!cooldownEnd) return;

        const tick = () => {
            const left = Math.max(0, cooldownEnd - Date.now());
            setRemaining(left);
            if (left <= 0) {
                setCooldownEnd(null);
            }
        };

        tick();
        const id = setInterval(tick, 250);
        return () => clearInterval(id);
    }, [cooldownEnd]);

    const handleRefresh = useCallback(() => {
        if (cooldownEnd && Date.now() < cooldownEnd) return;
        onRefresh?.();
        setCooldownEnd(Date.now() + COOLDOWN_MS);
    }, [cooldownEnd, onRefresh]);

    const isCoolingDown = cooldownEnd && remaining > 0;

    const formatRemaining = (ms) => {
        const totalSec = Math.ceil(ms / 1000);
        const min = Math.floor(totalSec / 60);
        const sec = totalSec % 60;
        return `${min}:${sec.toString().padStart(2, '0')}`;
    };

    return (
        <motion.div
            initial={{ opacity: 0, y: -16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.7, ease: [0.16, 1, 0.3, 1] }}
            className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"
        >
            <h1 className="flex items-center gap-3 text-2xl font-display tracking-normal text-fg sm:text-3xl">
                <span className="flex items-center justify-center w-10 h-10 rounded-xl bg-gradient-accent text-white shadow-sm shadow-accent/20">
                    {icon}
                </span>
                {title}
            </h1>
            <div className="flex flex-wrap items-center gap-2">
                <button
                    onClick={handleRefresh}
                    disabled={loading || isCoolingDown}
                    title={isCoolingDown ? `Cooldown: ${formatRemaining(remaining)}` : 'Yenile'}
                    className="flex items-center gap-2 rounded-lg border border-border-default bg-bg-elevated px-4 py-2 text-sm text-fg-muted transition-all duration-200 hover:bg-surface hover:text-fg hover:-translate-y-0.5 disabled:opacity-50 disabled:hover:translate-y-0"
                >
                    {loading ? (
                        <>
                            <RefreshCw className="h-4 w-4 animate-spin" />
                            Yükleniyor
                        </>
                    ) : isCoolingDown ? (
                        <>
                            <Clock className="h-4 w-4" />
                            {formatRemaining(remaining)}
                        </>
                    ) : (
                        <>
                            <RefreshCw className="h-4 w-4" />
                            Yenile
                        </>
                    )}
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
