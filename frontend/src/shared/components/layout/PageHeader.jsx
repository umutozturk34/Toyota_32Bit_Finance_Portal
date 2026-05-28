import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { motion } from 'framer-motion';
import { useLocation } from 'react-router-dom';
import { Clock } from 'lucide-react';
import { RefreshCw } from '../feedback/AnimatedIcons';
import AdminToolbar from '../admin/AdminToolbar';
import useAppStore from '../../stores/useAppStore';
import Button from '../buttons/Button';

const COOLDOWN_MS = 3 * 1000;

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
    const { t } = useTranslation();
    const { pathname } = useLocation();
    const setCooldown = useAppStore((s) => s.setCooldown);
    const cooldownEnd = useAppStore((s) => s.getCooldownEnd(pathname));
    const [remaining, setRemaining] = useState(() => Math.max(0, cooldownEnd - Date.now()));

    useEffect(() => {
        if (!cooldownEnd || cooldownEnd <= Date.now()) return;
        const tick = () => {
            const left = Math.max(0, cooldownEnd - Date.now());
            setRemaining(left);
        };
        tick();
        const id = setInterval(tick, 250);
        return () => clearInterval(id);
    }, [cooldownEnd]);

    const handleRefresh = useCallback(() => {
        if (cooldownEnd && Date.now() < cooldownEnd) return;
        onRefresh?.();
        setCooldown(pathname, Date.now() + COOLDOWN_MS);
    }, [cooldownEnd, onRefresh, setCooldown, pathname]);

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
            <h1 className="flex items-center gap-2 sm:gap-3 text-xl font-display tracking-normal text-fg sm:text-3xl min-w-0">
                <span className="flex items-center justify-center w-9 h-9 sm:w-10 sm:h-10 rounded-xl bg-gradient-accent text-white shadow-sm shadow-accent/20 shrink-0">
                    {icon}
                </span>
                <span className="min-w-0 break-words">{title}</span>
            </h1>
            <div className="flex flex-wrap items-center gap-2">
                <Button
                    variant="secondary"
                    size="md"
                    onClick={handleRefresh}
                    disabled={loading || isCoolingDown}
                    loading={loading}
                    leftIcon={isCoolingDown && !loading ? <Clock className="h-4 w-4" /> : (!loading ? <RefreshCw className="h-4 w-4" /> : null)}
                    title={isCoolingDown ? t('pageHeader.cooldown', { time: formatRemaining(remaining) }) : t('pageHeader.refresh')}
                    motionPreset="tapHover"
                >
                    {loading ? t('common.loading') : isCoolingDown ? formatRemaining(remaining) : t('pageHeader.refresh')}
                </Button>
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
