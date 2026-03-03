import { motion } from 'framer-motion';
import { AlertTriangle, RefreshCw } from 'lucide-react';

export default function ErrorState({ message, onRetry }) {
    return (
        <div className="flex min-h-[60vh] flex-col items-center justify-center gap-4">
            <motion.div
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                className="flex flex-col items-center gap-3 rounded-lg border border-danger/30 bg-danger/5 px-6 py-5"
            >
                <AlertTriangle className="h-7 w-7 text-danger" />
                <p className="text-fg text-sm">{message}</p>
            </motion.div>
            <button
                onClick={onRetry}
                className="flex items-center gap-2 rounded-md border border-border-default bg-surface px-4 py-2 text-sm text-fg transition-colors duration-150 hover:bg-surface-hover"
            >
                <RefreshCw className="h-4 w-4" />
                Tekrar Dene
            </button>
        </div>
    );
}
