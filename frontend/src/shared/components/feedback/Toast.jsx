import { useState, useEffect, useCallback, useRef } from 'react';
import { createPortal } from 'react-dom';
import { motion } from 'framer-motion';
import { AnimatePresence } from 'framer-motion';
import { CheckCircle, Info, X, ShieldAlert } from 'lucide-react';
import { AlertTriangle, AlertCircle } from './AnimatedIcons';
import { TIMINGS, LIMITS } from '../../config/uiConfig';
import { _registerToastFireHandler } from './toastBus';

const ICONS = {
  success: CheckCircle,
  warning: AlertTriangle,
  error: AlertCircle,
  info: Info,
  rateLimit: ShieldAlert,
};

const COLORS = {
  success: { border: 'border-success/30', bg: 'from-success/8', icon: 'bg-success/10 text-success' },
  warning: { border: 'border-warning/30', bg: 'from-warning/8', icon: 'bg-warning/10 text-warning' },
  error: { border: 'border-danger/30', bg: 'from-danger/8', icon: 'bg-danger/10 text-danger' },
  info: { border: 'border-accent/30', bg: 'from-accent/8', icon: 'bg-accent/10 text-accent' },
  rateLimit: { border: 'border-warning/30', bg: 'from-warning/8', icon: 'bg-warning/10 text-warning' },
};

export default function ToastContainer() {
  const [items, setItems] = useState([]);
  const idRef = useRef(0);

  useEffect(() => {
    _registerToastFireHandler((data) => {
      setItems((prev) => {
        if (data.dedupeKey) {
          const idx = prev.findIndex((t) => t.dedupeKey === data.dedupeKey);
          if (idx >= 0) {
            const next = [...prev];
            next[idx] = { ...prev[idx], ...data, id: prev[idx].id };
            return next;
          }
        }
        const id = ++idRef.current;
        return [...prev.slice(-4), { ...data, id }];
      });
    });
    return () => { _registerToastFireHandler(null); };
  }, []);

  const dismiss = useCallback((id) => {
    setItems((prev) => prev.filter((t) => t.id !== id));
  }, []);

  return createPortal(
    <div
      style={{ isolation: 'isolate' }}
      className="fixed top-4 left-1/2 -translate-x-1/2 z-[100] flex flex-col gap-2 w-[calc(100%-2rem)] max-w-md pointer-events-none"
    >
      <AnimatePresence>
        {items.map((item) => (
          <ToastItem key={item.id} item={item} onDismiss={dismiss} />
        ))}
      </AnimatePresence>
    </div>,
    document.body
  );
}

function ToastItem({ item, onDismiss }) {
  const { id, type = 'info', title, message, retryAfter } = item;
  const [countdown, setCountdown] = useState(retryAfter ? Number(retryAfter) : 0);
  const color = COLORS[type] || COLORS.info;
  const Icon = ICONS[type] || Info;

  useEffect(() => {
    const rawSec = retryAfter ? Number(retryAfter) : LIMITS.RATE_LIMIT_THROTTLE_DEFAULT;
    const duration = type === 'rateLimit'
      ? Math.min(Math.max(rawSec * 1000, TIMINGS.TOAST_RATE_LIMIT_MIN_MS), TIMINGS.TOAST_RATE_LIMIT_MAX_MS)
      : type === 'error' ? TIMINGS.TOAST_ERROR_DURATION_MS : TIMINGS.TOAST_DEFAULT_DURATION_MS;
    const timer = setTimeout(() => onDismiss(id), duration);
    return () => clearTimeout(timer);
  }, [id, type, retryAfter, onDismiss]);

  useEffect(() => {
    if (countdown <= 0) return;
    const interval = setInterval(() => {
      setCountdown((c) => {
        if (c <= 1) { clearInterval(interval); return 0; }
        return c - 1;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [countdown]);

  return (
    <motion.div
      initial={{ opacity: 0, y: -20, scale: 0.95 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, y: -20, scale: 0.95 }}
      transition={{ type: 'spring', stiffness: 400, damping: 30 }}
      className="pointer-events-auto"
    >
      <div className={`relative rounded-xl border ${color.border} toast-panel px-4 py-3.5 flex items-start gap-3 overflow-hidden`}>
        <div className={`absolute inset-0 bg-gradient-to-r ${color.bg} to-transparent pointer-events-none`} />
        <div className={`relative flex items-center justify-center w-9 h-9 rounded-lg ${color.icon} shrink-0`}>
          <Icon className="h-4.5 w-4.5" />
        </div>
        <div className="relative flex-1 min-w-0">
          <p className="text-sm font-semibold text-fg leading-tight">{title}</p>
          {message && <p className="text-xs text-fg-muted mt-0.5">{message}</p>}
          {type === 'rateLimit' && countdown > 0 && (
            <div className="mt-2 flex items-center gap-2">
              <div className="flex-1 h-1 rounded-full bg-surface overflow-hidden">
                <motion.div
                  className="h-full rounded-full bg-warning/60"
                  initial={{ width: '100%' }}
                  animate={{ width: '0%' }}
                  transition={{ duration: countdown, ease: 'linear' }}
                />
              </div>
              <span className="text-[11px] font-mono text-warning tabular-nums">{countdown}s</span>
            </div>
          )}
        </div>
        <button
          onClick={() => onDismiss(id)}
          className="relative flex items-center justify-center w-6 h-6 rounded-md text-fg-muted hover:text-fg hover:bg-surface transition-colors bg-transparent border-none cursor-pointer shrink-0"
        >
          <X className="h-3.5 w-3.5" />
        </button>
      </div>
    </motion.div>
  );
}
