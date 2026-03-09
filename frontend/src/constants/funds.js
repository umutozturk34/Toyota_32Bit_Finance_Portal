const getTrackedFunds = () => {
  const fundsEnv = import.meta.env.VITE_FUNDS_ALL || '';
  if (!fundsEnv) return [];
  return fundsEnv.split(',').map(s => s.trim()).filter(s => s.length > 0);
};

export const TRACKED_FUNDS = getTrackedFunds();

export const getFundCodes = () => TRACKED_FUNDS;

export const getFundDisplayName = (code) => code;
