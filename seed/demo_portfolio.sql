-- Demo portfolio: a realistic Turkish retail investor, seeded for clone-and-run demos.
-- Entry/exit prices are real historical closes (crypto converted USD->TRY at trade date).
-- Loaded AFTER market_data seed (needs tracked_assets / viop_contracts); snapshots are
-- recomputed by PortfolioBackfillService on backend startup.

-- pg_dump (market_data) clears search_path, so restore it before unqualified DML.
SET search_path TO public;

INSERT INTO portfolios (id, user_sub, name, created_at, version) VALUES
  (1, '22222222-2222-4222-8222-222222222222', 'Demo Portföy', '2022-08-01 10:00:00', 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO portfolio_positions (id, portfolio_id, quantity, entry_date, entry_price, tracked_asset_id, exit_date, exit_price, version) VALUES
  (1, 1, 600, '2023-02-15 10:00:00', 23.16, 872, NULL, NULL, 0),
  (2, 1, 150, '2023-05-10 10:00:00', 126.8, 936, NULL, NULL, 0),
  (3, 1, 400, '2023-08-21 10:00:00', 36.925, 834, NULL, NULL, 0),
  (4, 1, 200, '2024-01-15 10:00:00', 160.3, 891, NULL, NULL, 0),
  (5, 1, 80, '2022-11-03 10:00:00', 974.7843, 87, NULL, NULL, 0),
  (6, 1, 200, '2024-03-18 10:00:00', 25.9588, 92, NULL, NULL, 0),
  (7, 1, 3000, '2023-01-09 10:00:00', 18.7836, 632, NULL, NULL, 0),
  (8, 1, 1500, '2023-09-04 10:00:00', 28.9899, 633, NULL, NULL, 0),
  (9, 1, 0.04, '2023-10-05 10:00:00', 766166.2949, 1, NULL, NULL, 0),
  (10, 1, 0.6, '2024-02-12 10:00:00', 76860.0269, 2, NULL, NULL, 0),
  (11, 1, 5000, '2023-03-01 10:00:00', 80.7961, 106, NULL, NULL, 0),
  (12, 1, 500, '2022-12-01 10:00:00', 21.24, 867, '2024-09-16 10:00:00', 23.81, 0),
  (13, 1, 100, '2023-01-10 10:00:00', 65.0, 958, '2023-11-20 10:00:00', 152.125, 0),
  (14, 1, 10, '2023-05-02 10:00:00', 427.3811, 5, '2024-03-25 10:00:00', 5888.7754, 0),
  (15, 1, 3000, '2023-06-15 10:00:00', 2229.5448, 107, '2025-01-10 10:00:00', 3313.614, 0),
  (16, 1, 1000, '2022-08-01 10:00:00', 17.9261, 632, '2023-07-05 10:00:00', 26.0503, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO portfolio_derivative_positions (id, portfolio_id, viop_contract_id, direction, entry_date, entry_price, quantity_lot, close_date, close_price, close_reason, created_at, updated_at, version) VALUES
  (1, 1, 140, 'LONG', '2025-04-10', 55.7, 5, NULL, NULL, NULL, now(), now(), 0)
ON CONFLICT (id) DO NOTHING;

SELECT setval('portfolios_id_seq', GREATEST((SELECT COALESCE(max(id),1) FROM portfolios), 1));
SELECT setval('portfolio_positions_id_seq', GREATEST((SELECT COALESCE(max(id),1) FROM portfolio_positions), 1));
SELECT setval('portfolio_derivative_positions_id_seq', GREATEST((SELECT COALESCE(max(id),1) FROM portfolio_derivative_positions), 1));

