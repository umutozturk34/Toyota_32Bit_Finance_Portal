import { Loader2 } from './AnimatedIcons';

export default function LoadingState({ message = 'Veriler yükleniyor…' }) {
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
                <span className="text-fg-muted text-sm font-medium">{message}</span>
            </motion.div>
        </div>
    );
}
