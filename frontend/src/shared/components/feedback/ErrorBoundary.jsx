import { Component } from 'react';
import { motion } from 'framer-motion';
import { AlertOctagon, RefreshCw, Home } from 'lucide-react';

export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { error: null, occurredAt: null };
  }

  static getDerivedStateFromError(error) {
    return { error, occurredAt: new Date() };
  }

  componentDidCatch(error, info) {
    if (typeof console !== 'undefined' && console.error) {
      console.error('ErrorBoundary caught:', error, info?.componentStack);
    }
  }

  handleRetry = () => this.setState({ error: null, occurredAt: null });

  handleHome = () => {
    this.setState({ error: null, occurredAt: null });
    if (typeof window !== 'undefined') window.location.assign('/');
  };

  render() {
    if (!this.state.error) return this.props.children;

    const { error, occurredAt } = this.state;
    const time = occurredAt
      ? occurredAt.toLocaleTimeString('tr-TR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
      : '--:--:--';
    const name = error?.name || 'Error';
    const message = error?.message || 'Beklenmeyen bir hata oluştu.';

    return (
      <div className="flex items-center justify-center min-h-[70vh] p-6">
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.32, ease: [0.16, 1, 0.3, 1] }}
          className="relative w-full max-w-md"
        >
          <span
            aria-hidden
            className="pointer-events-none absolute -top-12 left-1/2 -translate-x-1/2 h-32 w-72 rounded-full blur-3xl"
            style={{ background: 'radial-gradient(circle, rgba(99,102,241,0.18), transparent 65%)' }}
          />

          <div className="relative rounded-2xl border border-border-default bg-bg-elevated backdrop-blur-md overflow-hidden">
            <div className="flex items-center justify-between gap-2 px-4 py-2 border-b border-border-default/60 bg-bg-base/40">
              <div className="flex items-center gap-1.5">
                <span className="relative inline-flex h-1.5 w-1.5">
                  <span className="absolute inset-0 rounded-full bg-danger animate-ping opacity-50" />
                  <span className="relative block h-1.5 w-1.5 rounded-full bg-danger" />
                </span>
                <span className="font-mono text-[10px] tracking-[0.18em] uppercase text-fg-subtle">
                  runtime · {time}
                </span>
              </div>
              <span className="font-mono text-[10px] tracking-tight text-fg-subtle/70 truncate max-w-[12rem]">
                {name}
              </span>
            </div>

            <div className="flex flex-col items-center text-center gap-4 px-8 py-9">
              <motion.div
                initial={{ scale: 0.7, opacity: 0 }}
                animate={{ scale: 1, opacity: 1 }}
                transition={{ delay: 0.1, type: 'spring', stiffness: 280, damping: 22 }}
                className="relative flex items-center justify-center w-14 h-14 rounded-2xl bg-accent/10"
                style={{ boxShadow: '0 0 32px -4px rgba(99,102,241,0.35)' }}
              >
                <AlertOctagon className="h-6 w-6 text-accent" strokeWidth={1.75} />
              </motion.div>

              <div className="space-y-1.5">
                <h2 className="font-mono text-[11px] tracking-[0.22em] uppercase text-accent">
                  Bir Aksaklık
                </h2>
                <p className="text-base font-semibold text-fg leading-snug">
                  Sayfa görüntülenirken bir hata oluştu
                </p>
                <p className="text-xs text-fg-muted leading-relaxed max-w-[28rem] mx-auto">
                  {message}
                </p>
              </div>

              <div className="flex items-center gap-2 pt-1">
                <button
                  type="button"
                  onClick={this.handleRetry}
                  className="flex items-center gap-1.5 rounded-lg bg-accent px-3.5 py-1.5 text-xs font-semibold text-white hover:bg-accent-bright transition-colors border-none cursor-pointer"
                >
                  <RefreshCw className="h-3 w-3" strokeWidth={2.2} />
                  Yeniden dene
                </button>
                <button
                  type="button"
                  onClick={this.handleHome}
                  className="flex items-center gap-1.5 rounded-lg border border-border-default bg-transparent px-3.5 py-1.5 text-xs font-medium text-fg-muted hover:text-fg hover:bg-surface transition-colors cursor-pointer"
                >
                  <Home className="h-3 w-3" strokeWidth={2.2} />
                  Ana sayfaya dön
                </button>
              </div>
            </div>

            <div className="px-4 py-2 border-t border-border-default/60 bg-bg-base/40">
              <span className="font-mono text-[9px] tracking-[0.2em] uppercase text-fg-subtle/60">
                kayıt geliştirici konsoluna yazıldı
              </span>
            </div>
          </div>
        </motion.div>
      </div>
    );
  }
}
