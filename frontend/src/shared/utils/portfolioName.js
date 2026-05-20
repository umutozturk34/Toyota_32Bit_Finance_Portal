const AUTO_DEFAULT_NAMES = new Set(['Demo Portföy', 'Demo Portfolio']);

export function portfolioName(t, portfolio) {
  const raw = portfolio?.name ?? '';
  if (AUTO_DEFAULT_NAMES.has(raw)) {
    return t('portfolio.onboarding.defaultName', { defaultValue: raw });
  }
  return raw;
}

export function localizePortfolioName(t, name) {
  if (!name) return '';
  if (AUTO_DEFAULT_NAMES.has(name)) {
    return t('portfolio.onboarding.defaultName', { defaultValue: name });
  }
  return name;
}
