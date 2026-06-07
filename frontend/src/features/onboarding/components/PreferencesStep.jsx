import { UserCog, Sun, Moon } from 'lucide-react';
import SectionEnter from './SectionEnter';

export default function PreferencesStep({ themePreference, setThemePreference, language, onLanguageChange, t }) {
  const themeOptions = [
    { value: 'LIGHT', label: t('onboarding.step.preferences.themeLight'), Icon: Sun },
    { value: 'DARK', label: t('onboarding.step.preferences.themeDark'), Icon: Moon },
  ];
  const langOptions = [
    { value: 'tr', label: 'Türkçe', sub: 'TR' },
    { value: 'en', label: 'English', sub: 'EN' },
  ];

  return (
    <div className="space-y-5 sm:space-y-7">
      <SectionEnter>
        <div className="text-center">
          <div className="mx-auto mb-3 flex items-center justify-center w-12 h-12 rounded-2xl bg-gradient-accent text-white shadow-lg shadow-accent/25">
            <UserCog className="h-5 w-5" />
          </div>
          <h2 className="text-xl sm:text-2xl font-display font-bold text-fg">{t('onboarding.step.preferences.title')}</h2>
          <p className="mt-1.5 text-xs sm:text-sm text-fg-muted max-w-sm mx-auto">
            {t('onboarding.step.preferences.subtitle')}
          </p>
        </div>
      </SectionEnter>

      <SectionEnter delay={0.08}>
        <div>
          <div className="mb-2 text-[11px] font-mono uppercase tracking-[0.18em] text-fg-subtle">
            {t('onboarding.step.preferences.theme')}
          </div>
          <div className="grid grid-cols-2 gap-2 sm:gap-3">
            {themeOptions.map(({ value, label, Icon }) => {
              const active = themePreference === value;
              return (
                <button
                  key={value}
                  type="button"
                  onClick={() => setThemePreference(value)}
                  className={`relative overflow-hidden rounded-2xl border px-3 py-3 sm:px-4 sm:py-4 text-left transition-all duration-200 cursor-pointer min-h-[88px] ${
                    active
                      ? 'border-accent bg-accent/[0.08] shadow-lg shadow-accent/10'
                      : 'border-border-default bg-bg-elevated hover:border-accent/40 hover:bg-bg-elevated/80'
                  }`}
                >
                  <div className="flex items-center gap-2 sm:gap-2.5 min-w-0">
                    <span className={`flex items-center justify-center w-8 h-8 sm:w-9 sm:h-9 rounded-xl shrink-0 ${
                      active ? 'bg-gradient-accent text-white' : 'bg-surface text-fg-muted'
                    }`}>
                      <Icon className="h-4 w-4" />
                    </span>
                    <span className={`text-xs sm:text-sm font-semibold truncate ${active ? 'text-fg' : 'text-fg-muted'}`}>
                      {label}
                    </span>
                  </div>
                  <div
                    aria-hidden="true"
                    className="mt-3 flex items-center gap-1.5"
                  >
                    <span className={`h-2 rounded-full flex-1 ${
                      value === 'LIGHT' ? 'bg-[#0052FF]' : 'bg-[#6366f1]'
                    }`} />
                    <span className={`h-2 rounded-full flex-1 ${
                      value === 'LIGHT' ? 'bg-[#94A3B8]/60' : 'bg-[#8b8b9a]/60'
                    }`} />
                    <span className={`h-2 rounded-full flex-1 ${
                      value === 'LIGHT' ? 'bg-[#E8EDF5]' : 'bg-[#1a1a24]'
                    }`} />
                  </div>
                </button>
              );
            })}
          </div>
        </div>
      </SectionEnter>

      <SectionEnter delay={0.14}>
        <div>
          <div className="mb-2 text-[11px] font-mono uppercase tracking-[0.18em] text-fg-subtle">
            {t('onboarding.step.preferences.language')}
          </div>
          <div className="grid grid-cols-2 gap-2 sm:gap-3">
            {langOptions.map(({ value, label, sub }) => {
              const active = (language || '').slice(0, 2) === value;
              return (
                <button
                  key={value}
                  type="button"
                  onClick={() => onLanguageChange(value)}
                  className={`flex items-center justify-between gap-2 rounded-2xl border px-3 py-3 sm:px-4 sm:py-4 min-h-[60px] transition-all duration-200 cursor-pointer ${
                    active
                      ? 'border-accent bg-accent/[0.08] shadow-lg shadow-accent/10'
                      : 'border-border-default bg-bg-elevated hover:border-accent/40 hover:bg-bg-elevated/80'
                  }`}
                >
                  <div className="min-w-0">
                    <div className={`text-xs sm:text-sm font-semibold truncate ${active ? 'text-fg' : 'text-fg-muted'}`}>
                      {label}
                    </div>
                    <div className="mt-0.5 text-[10px] font-mono uppercase tracking-[0.18em] text-fg-subtle">
                      {sub}
                    </div>
                  </div>
                  <span className={`text-[11px] font-mono font-bold tracking-wider shrink-0 ${
                    active ? 'text-accent' : 'text-fg-subtle'
                  }`}>
                    {sub}
                  </span>
                </button>
              );
            })}
          </div>
        </div>
      </SectionEnter>
    </div>
  );
}
