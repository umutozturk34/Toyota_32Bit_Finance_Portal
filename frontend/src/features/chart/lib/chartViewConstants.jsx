import { LineChart, BarChart2, RefreshCw, Activity } from 'lucide-react';

export const ASSET_ICONS = {
  BIST: <BarChart2 className="w-4 h-4" />,
  CRYPTO: <Activity className="w-4 h-4" />,
  FOREX: <RefreshCw className="w-4 h-4" />,
  FUND: <LineChart className="w-4 h-4" />,
};

export const ASSET_TYPE_KEYS = {
  BIST: 'STOCK',
  CRYPTO: 'CRYPTO',
  FOREX: 'FOREX',
  FUND: 'FUND',
};

export const ROUTE_TO_ASSET_TYPE = {
  bist: 'BIST',
  crypto: 'CRYPTO',
  forex: 'FOREX',
  fund: 'FUND',
};

export const ASSET_TYPE_TO_ROUTE = {
  BIST: 'bist',
  CRYPTO: 'crypto',
  FOREX: 'forex',
  FUND: 'fund',
};

export const ensureBistSuffix = (code) => (code.endsWith('.IS') ? code : `${code}.IS`);

export const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.08, delayChildren: 0.1 },
  },
};

export const itemVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.5, ease: [0.16, 1, 0.3, 1] } },
};
