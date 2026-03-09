import { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
    Activity, Play, CheckCircle2, XCircle, Clock,
    RefreshCw, Loader2, Zap, Database,
    TrendingUp, DollarSign, Bitcoin, Briefcase,
} from 'lucide-react';
import { adminService } from '../services/marketService';
import { containerVariants, cardVariants } from '../utils/animations';
import PageHeader from '../components/PageHeader';

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
        <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ${cfg.bg} ${cfg.color} border ${cfg.border}`}>
            <Icon className={`h-3 w-3 ${cfg.spin ? 'animate-spin' : ''}`} />
            {cfg.label}
        </span>
    );
}

export default function Tasks() {
    const [taskData, setTaskData] = useState({ running: [], history: [], runningCount: 0 });
    const [triggering, setTriggering] = useState({});
    const [autoRefresh, setAutoRefresh] = useState(true);

    const fetchStatus = useCallback(async () => {
        try {
            const data = await adminService.getTaskStatus();
            setTaskData(data);
        } catch {
        }
    }, []);

    useEffect(() => {
        fetchStatus();
    }, [fetchStatus]);

    useEffect(() => {
        if (!autoRefresh) return;
        const id = setInterval(fetchStatus, 3000);
        return () => clearInterval(id);
    }, [autoRefresh, fetchStatus]);

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
        <div className="space-y-6 py-6">
            <PageHeader
                icon={<Activity className="h-5 w-5" />}
                title="Task Manager"
                onRefresh={fetchStatus}
            />

            <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                    {taskData.runningCount > 0 && (
                        <span className="inline-flex items-center gap-1.5 rounded-full bg-accent/10 px-3 py-1.5 text-sm font-medium text-accent border border-accent/20">
                            <Loader2 className="h-3.5 w-3.5 animate-spin" />
                            {taskData.runningCount} task running
                        </span>
                    )}
                </div>
                <button
                    onClick={() => setAutoRefresh(!autoRefresh)}
                    className={`flex items-center gap-2 rounded-md px-3 py-1.5 text-xs font-medium transition-colors border ${
                        autoRefresh
                            ? 'bg-accent/10 text-accent border-accent/20'
                            : 'bg-bg-elevated text-fg-muted border-border-default hover:text-fg'
                    }`}
                >
                    <RefreshCw className={`h-3.5 w-3.5 ${autoRefresh ? 'animate-spin-slow' : ''}`} />
                    Auto Refresh {autoRefresh ? 'ON' : 'OFF'}
                </button>
            </div>

            <motion.div
                variants={containerVariants(0.08)}
                initial="hidden"
                animate="show"
                className="grid gap-4 sm:grid-cols-2"
            >
                {TASK_GROUPS.map(({ category, icon: CatIcon, color, bg, tasks }) => (
                    <motion.div
                        key={category}
                        variants={cardVariants}
                        className="rounded-xl border border-border-default bg-bg-elevated p-5 space-y-4"
                    >
                        <div className="flex items-center gap-3">
                            <span className={`flex items-center justify-center w-9 h-9 rounded-lg ${bg} ${color}`}>
                                <CatIcon className="w-4.5 h-4.5" />
                            </span>
                            <h3 className="text-sm font-semibold text-fg">{category}</h3>
                        </div>
                        <div className="flex flex-wrap gap-2">
                            {tasks.map(({ key, label, trigger }) => {
                                const isRunning = runningSet.has(key);
                                const isTriggering = triggering[key];
                                return (
                                    <button
                                        key={key}
                                        onClick={() => handleTrigger(key, trigger)}
                                        disabled={isRunning || isTriggering}
                                        className="flex items-center gap-2 rounded-md border border-border-default bg-bg-base px-3 py-2 text-xs font-medium text-fg-muted transition-all hover:bg-surface hover:text-fg hover:border-border-hover disabled:opacity-50 disabled:cursor-not-allowed"
                                    >
                                        {isRunning ? (
                                            <Loader2 className="h-3.5 w-3.5 animate-spin text-accent" />
                                        ) : isTriggering ? (
                                            <Loader2 className="h-3.5 w-3.5 animate-spin" />
                                        ) : (
                                            <Play className="h-3.5 w-3.5" />
                                        )}
                                        {label}
                                    </button>
                                );
                            })}
                        </div>
                    </motion.div>
                ))}
            </motion.div>

            {taskData.running.length > 0 && (
                <div className="space-y-3">
                    <h2 className="flex items-center gap-2 text-sm font-semibold text-fg">
                        <Zap className="h-4 w-4 text-accent" />
                        Active Tasks
                    </h2>
                    <AnimatePresence>
                        <motion.div
                            variants={containerVariants(0.05)}
                            initial="hidden"
                            animate="show"
                            className="space-y-2"
                        >
                            {taskData.running.map((t) => (
                                <motion.div
                                    key={t.type}
                                    variants={cardVariants}
                                    layout
                                    className="flex items-center justify-between rounded-lg border border-accent/20 bg-accent/5 px-4 py-3"
                                >
                                    <div className="flex items-center gap-3">
                                        <StatusBadge status="RUNNING" />
                                        <span className="text-sm font-medium text-fg">{t.type}</span>
                                    </div>
                                    <div className="flex items-center gap-3 text-xs text-fg-muted">
                                        <span className="flex items-center gap-1">
                                            <Clock className="h-3 w-3" />
                                            {formatDuration(t.durationMs)}
                                        </span>
                                        <span>{formatTime(t.startedAt)}</span>
                                    </div>
                                </motion.div>
                            ))}
                        </motion.div>
                    </AnimatePresence>
                </div>
            )}

            <div className="space-y-3">
                <h2 className="flex items-center gap-2 text-sm font-semibold text-fg">
                    <Database className="h-4 w-4 text-fg-muted" />
                    Task History
                </h2>
                {taskData.history.length === 0 ? (
                    <div className="rounded-lg border border-border-default bg-bg-elevated px-4 py-8 text-center text-sm text-fg-muted">
                        No task history yet. Trigger a task to see it here.
                    </div>
                ) : (
                    <div className="overflow-hidden rounded-lg border border-border-default">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b border-border-default bg-bg-elevated">
                                    <th className="px-4 py-2.5 text-left text-xs font-medium text-fg-muted uppercase tracking-wider">Task</th>
                                    <th className="px-4 py-2.5 text-left text-xs font-medium text-fg-muted uppercase tracking-wider">Status</th>
                                    <th className="px-4 py-2.5 text-left text-xs font-medium text-fg-muted uppercase tracking-wider hidden sm:table-cell">Started</th>
                                    <th className="px-4 py-2.5 text-left text-xs font-medium text-fg-muted uppercase tracking-wider">Duration</th>
                                    <th className="px-4 py-2.5 text-left text-xs font-medium text-fg-muted uppercase tracking-wider hidden md:table-cell">Details</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-border-default">
                                {taskData.history.map((t, i) => (
                                    <tr key={`${t.type}-${t.startedAt}-${i}`} className="bg-bg-base hover:bg-surface transition-colors">
                                        <td className="px-4 py-3 font-medium text-fg">{t.type}</td>
                                        <td className="px-4 py-3"><StatusBadge status={t.status} /></td>
                                        <td className="px-4 py-3 text-fg-muted hidden sm:table-cell">{formatTime(t.startedAt)}</td>
                                        <td className="px-4 py-3 text-fg-muted">{formatDuration(t.durationMs)}</td>
                                        <td className="px-4 py-3 text-fg-subtle text-xs max-w-[200px] truncate hidden md:table-cell">
                                            {t.error || t.message}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>
        </div>
    );
}
