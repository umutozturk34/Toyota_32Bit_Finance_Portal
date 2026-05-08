import { AnimatePresence } from 'framer-motion';
import { Sparkles, BarChart3, Wallet, Bell, Shield, ArrowRight } from 'lucide-react';
import { useUserPreferences, useUpdateUserPreferences } from '../../shared/hooks/useUserPreferences';

const HIGHLIGHTS = [
  { Icon: BarChart3, title: 'Çoklu Piyasa', body: 'Hisse, kripto, emtia, fon, döviz, tahvil tek noktada' },
  { Icon: Wallet, title: 'Hipotetik Portföy', body: 'Geçmiş tarihli pozisyonlarla ne olurdu senaryoları' },
  { Icon: Bell, title: 'Akıllı Bildirimler', body: 'Fiyat alarmları ve takip listesi (yakında)' },
  { Icon: Shield, title: 'Güvenli Erişim', body: 'Keycloak, JWT, opsiyonel iki adımlı doğrulama' },
];

export default function OnboardingGate() {
  const { preferences, isLoading } = useUserPreferences();
  const updatePreferences = useUpdateUserPreferences();

  const show = !isLoading && preferences && preferences.userSub && preferences.onboardingCompleted === false;

  const handleComplete = () => {
    updatePreferences.mutate({ onboardingCompleted: true });
  };

  return (
    <AnimatePresence>
      {show && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center p-4">
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.25 }}
            className="absolute inset-0 modal-overlay backdrop-blur-md"
          />
          <motion.div
            initial={{ opacity: 0, scale: 0.96, y: 12 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.96, y: 12 }}
            transition={{ type: 'spring', stiffness: 280, damping: 28 }}
            className="relative w-full max-w-lg rounded-2xl border border-border-default modal-panel overflow-hidden"
          >
            <span className="pointer-events-none absolute -top-24 left-1/2 -translate-x-1/2 w-72 h-48 rounded-full bg-accent/[0.08] blur-[90px]" aria-hidden="true" />
            <span className="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-accent/50 to-transparent" />

            <div className="relative px-6 pt-7 pb-5 text-center">
              <motion.div
                initial={{ scale: 0 }}
                animate={{ scale: 1 }}
                transition={{ type: 'spring', stiffness: 280, damping: 18, delay: 0.1 }}
                className="mx-auto flex items-center justify-center w-14 h-14 rounded-2xl bg-gradient-accent text-white shadow-lg shadow-accent/30"
              >
                <Sparkles className="h-6 w-6" />
              </motion.div>
              <motion.h2
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.18 }}
                className="mt-4 text-xl font-display text-fg"
              >
                Finance Portal'a Hoş Geldin
              </motion.h2>
              <motion.p
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.24 }}
                className="mt-1.5 text-sm text-fg-muted max-w-sm mx-auto"
              >
                Piyasa verisi, portföy takibi ve bildirimleri tek arayüzde toplayan finans paneli.
              </motion.p>
            </div>

            <div className="px-6 pb-5 space-y-2">
              {HIGHLIGHTS.map((item, i) => (
                <motion.div
                  key={item.title}
                  initial={{ opacity: 0, x: -8 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: 0.3 + i * 0.05 }}
                  className="flex items-start gap-3 rounded-xl border border-border-default bg-bg-base px-3.5 py-2.5"
                >
                  <span className="flex items-center justify-center w-8 h-8 rounded-lg bg-accent/10 text-accent shrink-0">
                    <item.Icon className="h-4 w-4" />
                  </span>
                  <div className="min-w-0">
                    <div className="text-[13px] font-semibold text-fg">{item.title}</div>
                    <div className="text-[11px] text-fg-muted leading-relaxed">{item.body}</div>
                  </div>
                </motion.div>
              ))}
            </div>

            <div className="px-6 pb-6">
              <motion.button
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.55 }}
                onClick={handleComplete}
                disabled={updatePreferences.isPending}
                className="w-full flex items-center justify-center gap-2 rounded-xl py-3 text-sm font-semibold text-white bg-gradient-accent transition-all duration-200 hover:-translate-y-0.5 active:scale-[0.98] disabled:opacity-50 disabled:cursor-wait border-none cursor-pointer"
              >
                Başlayalım
                <ArrowRight className="h-4 w-4" />
              </motion.button>
              <p className="mt-2 text-center text-[10px] text-fg-subtle">
                Bu pencereyi tekrar görmeyeceksin — Ayarlar'dan tüm seçenekleri yönetebilirsin.
              </p>
            </div>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
}
