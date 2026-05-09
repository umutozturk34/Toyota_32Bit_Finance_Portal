import { AlertTriangle, RefreshCw } from './AnimatedIcons';

import { motion } from 'framer-motion';
export default function ErrorState({ message, onRetry }) {
    return (
        <div className="flex min-h-[60vh] flex-col items-center justify-center gap-5">
            <motion.div
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                className="flex flex-col items-center gap-4 rounded-2xl border border-danger/20 bg-bg-elevated px-8 py-8 card-hover"
            >
                <div className="flex items-center justify-center w-14 h-14 rounded-xl bg-danger/10">
                    <AlertTriangle className="h-7 w-7 text-danger" />
                </div>
                <div className="text-center space-y-1">
                    <p className="text-sm font-semibold text-fg">Bir hata oluştu</p>
                    <p className="text-xs text-fg-muted max-w-xs">{message}</p>
                </div>
                {onRetry && (
                    <button
                        onClick={onRetry}
                        className="flex items-center gap-2 rounded-lg border border-border-default bg-bg-base px-5 py-2.5 text-sm font-medium text-fg transition-all duration-150 hover:bg-surface hover:border-border-hover cursor-pointer"
                    >
                        <RefreshCw className="h-4 w-4" />
                        Tekrar Dene
                    </button>
                )}
            </motion.div>
        </div>
    );
}
