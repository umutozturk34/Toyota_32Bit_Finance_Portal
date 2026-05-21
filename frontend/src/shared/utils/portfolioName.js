const AUTO_DEFAULT_NAMES = new Set(['Demo Portföy', 'Demo Portfolio']);

export function portfolioName(t, portfolio) {
  const raw = portfolio?.name ?? '';
  if (AUTO_DEFAULT_NAMES.has(raw)) {
    return t('portfolio.onboarding.defaultName', { defaultValue: raw });
  }
  return raw;
}
