import { useNavigate } from 'react-router-dom';

import { motion } from 'framer-motion';
export default function NotFound() {
  const navigate = useNavigate();

  return (
    <div className="relative flex flex-col items-center justify-center min-h-[70vh] overflow-hidden select-none">

      <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
        <motion.span
          initial={{ opacity: 0, scale: 0.8 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.8, ease: 'easeOut' }}
          className="text-[22rem] font-mono font-black leading-none tracking-tighter"
          style={{
            background: 'linear-gradient(180deg, rgba(99,102,241,0.12) 0%, transparent 80%)',
            WebkitBackgroundClip: 'text',
            WebkitTextFillColor: 'transparent',
          }}
        >
          404
        </motion.span>
      </div>

      <div className="relative z-10 flex flex-col items-center gap-6">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.3, duration: 0.6 }}
          className="flex flex-col items-center gap-2"
        >
          <div className="flex items-center gap-3 mb-2">
            <span className="h-px w-8 bg-accent/40" />
            <span className="text-xs font-mono text-accent tracking-[0.3em] uppercase">Sayfa Bulunamadı</span>
            <span className="h-px w-8 bg-accent/40" />
          </div>
          <h1 className="text-3xl font-bold text-fg tracking-tight">
            Aradığınız sayfa mevcut değil
          </h1>
          <p className="text-sm text-fg-muted max-w-sm text-center mt-1 leading-relaxed">
            Bu sayfa taşınmış, silinmiş veya hiç var olmamış olabilir.
          </p>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.6, duration: 0.5 }}
          className="flex items-center gap-3 mt-2"
        >
          <button
            onClick={() => navigate(-1)}
            className="px-5 py-2.5 rounded-xl border border-border-default text-sm font-medium text-fg-muted
                       hover:border-border-hover hover:text-fg transition-all duration-200"
          >
            Geri Dön
          </button>
          <button
            onClick={() => navigate('/')}
            className="px-5 py-2.5 rounded-xl bg-accent text-white text-sm font-medium
                       hover:bg-accent-bright transition-all duration-200
                       shadow-[0_0_20px_rgba(99,102,241,0.2)]"
          >
            Ana Sayfa
          </button>
        </motion.div>
      </div>
    </div>
  );
}
