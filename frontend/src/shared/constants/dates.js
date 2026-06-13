// Earliest date any date picker may reach: the first trading day of 2000, the floor of the EUR/TRY FX
// history. A date older than this has no EUR rate, so a multi-currency value/return can't be computed and
// renders as broken K/Z. Mirrors the backend floors (PortfolioProperties.minEntryDate and
// ScenarioService.EARLIEST_START); the backend is the authoritative guard, this just keeps the UI honest.
export const EARLIEST_DATA_DATE = '2000-01-04';
