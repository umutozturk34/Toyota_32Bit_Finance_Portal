import { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
    Activity, Play, CheckCircle2, XCircle, Clock,
    RefreshCw, Loader2, Zap, Database, X,
    TrendingUp, DollarSign, Bitcoin, Briefcase,
} from 'lucide-react';
import { adminService } from '../services/marketService';

const TASK_GROUPS = [
    {
        category: 'Crypto',
        icon: Bitcoin,
        color: 'text-warning',
        bg: 'bg-warning/10',
        tasks: [
            { key: 'crypto-snapshot', label: 'Snapshot', trigger: () => adminService.triggerCryptoSnapshot() },
            { key: 'crypto-candles', label: 'Candles', trigger: () => adminService.triggerCryptoCandles() },
            { key: 'crypto-full', label: 'Full Update', trigger: () => adminService.triggerCryptoFull() },
        ],
    },
    {
        category: 'Stocks',
        icon: TrendingUp,
        color: 'text-success',
        bg: 'bg-success/10',
        tasks: [
            { key: 'stock-snapshot', label: 'Snapshot', trigger: () => adminService.triggerStockSnapshot() },
            { key: 'stock-candles', label: 'Candles', trigger: () => adminService.triggerStockCandles() },
            { key: 'stock-full', label: 'Full Update', trigger: () => adminService.triggerStockFull() },
        ],
    },
    {
        category: 'Forex',
        icon: DollarSign,
        color: 'text-accent',
        bg: 'bg-accent/10',
        tasks: [
            { key: 'forex-snapshot', label: 'Snapshot', trigger: () => adminService.triggerForexSnapshot() },
            { key: 'forex-candles', label: 'Candles', trigger: () => adminService.triggerForexCandles() },
            { key: 'forex-full', label: 'Full Update', trigger: () => adminService.triggerForexFull() },
        ],
    },
    {
        category: 'Funds',
        icon: Briefcase,
        color: 'text-[#60a5fa]',
        bg: 'bg-[#60a5fa]/10',
        tasks: [
            { key: 'fund-snapshot', label: 'Snapshot', trigger: () => adminService.triggerFundSnapshot() },
            { key: 'fund-candles', label: 'Candles', trigger: () => adminService.triggerFundCandles() },
            { key: 'fund-full', label: 'Full Update', trigger: () => adminService.triggerFundFull() },
        ],
    },
];

function formatDuration(ms) {
    if (ms == null) return '';
    const s = Math.floor(ms / 1000);
    if (s < 60) return `${s}s`;
    const m = Math.floor(s / 60);
    const rem = s % 60;
    if (m < 60) return `${m}m ${rem}s`;
    const h = Math.floor(m / 60);
    return `${h}h ${m % 60}m`;
}

function formatTime(iso) {
    if (!iso) return '';
    const d = new Date(iso);
    return d.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

const statusConfig = {
    RUNNING:   { icon: Loader2, color: 'text-accent', bg: 'bg-accent/10', border: 'border-accent/30', label: 'Running', spin: true },
    COMPLETED: { icon: CheckCircle2, color: 'text-success', bg: 'bg-success/10', border: 'border-success/30', label: 'Completed', spin: false },
    FAILED:    { icon: XCircle, color: 'text-danger', bg: 'bg-danger/10', border: 'border-danger/30', label: 'Failed', spin: false },
};

function StatusBadge({ status }) {
    const cfg = statusConfig[status] || statusConfig.RUNNING;
    const Icon = cfg.icon;
    return (
        <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium ${cfg.bg} ${cfg.color} border ${cfg.border}`}>
            <Icon className={`h-2.5 w-2.5 ${cfg.spin ? 'animate-spin' : ''}`} />
            {cfg.label}
        </span>
    );
}

export default function TasksPanel({ open, onClose }) {
    const [taskData, setTaskData] = useState({ running: [], history: [], runningCount: 0 });
    const [triggering, setTriggering] = useState({});

    const fetchStatus = useCallback(async () => {
        try {
            const data = await adminService.getTaskStatus();
            setTaskData(data);
        } catch {}
    }, []);

    useEffect(() => {
        if (!open) return;
        fetchStatus();
        const id = setInterval(fetchStatus, 3000);
        return () => clearInterval(id);
    }, [open, fetchStatus]);

    const runningSet = new Set(taskData.running.map(t => t.type));

    const handleTrigger = async (key, triggerFn) => {
        setTriggering(p => ({ ...p, [key]: true }));
        try {
            await triggerFn();
            setTimeout(fetchStatus, 500);
        } catch (err) {
            const msg = err.response?.data?.message || err.message;
            alert(msg);
        } finally {
            setTriggering(p => ({ ...p, [key]: false }));
        }
    };

    return (
        <AnimatePresence>
            {open && (
                <>
                    <motion.div
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                        transition={{ duration: 0.15 }}
                        className="fixed inset-0 z-[60] bg-black/30 backdrop-blur-sm"
                        onClick={onClose}
                    />
                    <motion.div
                        initial={{ x: '100%' }}
                        animate={{ x: 0 }}
                        exit={{ x: '100%' }}
                        transition={{ type: 'spring', damping: 28, stiffness: 300 }}
                        className="fixed top-0 right-0 bottom-0 z-[70] w-[420px] max-w-[90vw] flex flex-col border-l border-border-default bg-bg-base overflow-hidden"
                    >
                        <div className="flex items-center justify-between px-4 py-3 border-b border-border-default bg-bg-elevated shrink-0">
                            <div className="flex items-center gap-2">
                                <Activity className="w-4 h-4 text-accent" />
                                <span className="text-sm font-semibold text-fg">Task Manager</span>
                                {taskData.runningCount > 0 && (
                                    <span className="inline-flex items-center gap-1 rounded-full bg-accent/10 px-2 py-0.5 text-[10px] font-medium text-accent border border-accent/20">
                                        <Loader2 className="h-2.5 w-2.5 animate-spin" />
                                        {taskData.runningCount}
                                    </span>
                                )}
                            </div>
                            <div className="flex items-center gap-1.5">
                                <button
                                    onClick={fetchStatus}
                                    className="p-1.5 rounded-md text-fg-muted hover:text-fg hover:bg-surface transition-colors"
                                    title="Refresh"
                                >
                                    <RefreshCw className="w-3.5 h-3.5" />
                                </button>
                                <button
                                    onClick={onClose}
                                    className="p-1.5 rounded-md text-fg-muted hover:text-fg hover:bg-surface transition-colors"
                                >
                                    <X className="w-4 h-4" />
                                </button>
                            </div>
                        </div>

                        <div className="flex-1 overflow-y-auto p-3 space-y-3" style={{ scrollbarWidth: 'thin' }}>
                            <div className="grid gap-2 grid-cols-2">
                                {TASK_GROUPS.map(({ category, icon: CatIcon, color, bg, tasks }) => (
                                    <div key={category} className="rounded-lg border border-border-default bg-bg-elevated p-3 space-y-2">
                                        <div className="flex items-center gap-2">
                                            <span className={`flex items-center justify-center w-6 h-6 rounded-md ${bg} ${color}`}>
                                                <CatIcon className="w-3 h-3" />
                                            </span>
                                            <span className="text-xs font-semibold text-fg">{category}</span>
                                        </div>
                                        <div className="flex flex-wrap gap-1">
                                            {tasks.map(({ key, label, trigger }) => {
                                                const isRunning = runningSet.has(key);
                                                const isTriggering = triggering[key];
                                                return (
                                                    <button
                                                        key={key}
                                                        onClick={() => handleTrigger(key, trigger)}
                                                        disabled={isRunning || isTriggering}
                                                        className="flex items-center gap-1 rounded border border-border-default bg-bg-base px-2 py-1 text-[10px] font-medium text-fg-muted transition-all hover:bg-surface hover:text-fg disabled:opacity-50 disabled:cursor-not-allowed"
                                                    >
                                                        {isRunning ? (
                                                            <Loader2 className="h-2.5 w-2.5 animate-spin text-accent" />
                                                        ) : isTriggering ? (
                                                            <Loader2 className="h-2.5 w-2.5 animate-spin" />
                                                        ) : (
                                                            <Play className="h-2.5 w-2.5" />
                                                        )}
                                                        {label}
                                                    </button>
                                                );
                                            })}
                                        </div>
                                    </div>
                                ))}
                            </div>

                            {taskData.running.length > 0 && (
                                <div className="space-y-1.5">
                                    <h3 className="flex items-center gap-1.5 text-xs font-semibold text-fg">
                                        <Zap className="h-3 w-3 text-accent" />
                                        Active
                                    </h3>
                                    {taskData.running.map((t) => (
                                        <div
                                            key={t.type}
                                            className="flex items-center justify-between rounded-md border border-accent/20 bg-accent/5 px-3 py-2"
                                        >
                                            <div className="flex items-center gap-2">
                                                <StatusBadge status="RUNNING" />
                                                <span className="text-xs font-medium text-fg">{t.type}</span>
                                            </div>
                                            <span className="flex items-center gap-1 text-[10px] text-fg-muted">
                                                <Clock className="h-2.5 w-2.5" />
                                                {formatDuration(t.durationMs)}
                                            </span>
                                        </div>
                                    ))}
                                </div>
                            )}

                            <div className="space-y-1.5">
                                <h3 className="flex items-center gap-1.5 text-xs font-semibold text-fg">
                                    <Database className="h-3 w-3 text-fg-muted" />
                                    History
                                </h3>
                                {taskData.history.length === 0 ? (
                                    <p className="text-[11px] text-fg-muted py-4 text-center">No task history yet</p>
                                ) : (
                                    <div className="space-y-1">
                                        {taskData.history.map((t, i) => (
                                            <div
                                                key={`${t.type}-${t.startedAt}-${i}`}
                                                className="flex items-center justify-between rounded-md border border-border-default bg-bg-elevated px-3 py-2"
                                            >
                                                <div className="flex items-center gap-2 min-w-0">
                                                    <StatusBadge status={t.status} />
                                                    <span className="text-xs font-medium text-fg truncate">{t.type}</span>
                                                </div>
                                                <div className="flex items-center gap-2 shrink-0 text-[10px] text-fg-muted">
                                                    <span>{formatDuration(t.durationMs)}</span>
                                                    <span>{formatTime(t.startedAt)}</span>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </div>
                        </div>
                    </motion.div>
                </>
            )}
        </AnimatePresence>
    );
}
