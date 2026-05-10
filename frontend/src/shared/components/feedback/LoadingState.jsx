import { Loader2 } from './AnimatedIcons';
import { useTranslation } from 'react-i18next';

import { motion } from 'framer-motion';
export default function LoadingState({ message }) {
    const { t } = useTranslation();
    const text = message ?? t('common.loadingData');
    return (
        <div className="flex min-h-[60vh] items-center justify-center">
            <motion.div
                initial={{ opacity: 0, scale: 0.9 }}
                animate={{ opacity: 1, scale: 1 }}
                className="flex flex-col items-center gap-4 rounded-2xl border border-border-default bg-bg-elevated px-10 py-10 card-hover"
            >
                <div className="relative">
                    <div className="absolute inset-0 rounded-full bg-accent/20 blur-xl animate-pulse-glow" />
                    <Loader2 className="relative h-8 w-8 animate-spin text-accent" />
                </div>
                <span className="text-fg-muted text-sm font-medium">{text}</span>
            </motion.div>
        </div>
    );
}
