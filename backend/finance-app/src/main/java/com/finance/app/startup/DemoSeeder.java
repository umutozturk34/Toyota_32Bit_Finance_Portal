package com.finance.app.startup;

import com.finance.common.model.TrackedAssetType;
import com.finance.market.core.service.TrackedAssetCommandService;
import com.finance.portfolio.derivative.dto.request.OpenDerivativePositionRequest;
import com.finance.portfolio.derivative.model.DerivativeDirection;
import com.finance.portfolio.derivative.service.DerivativePositionService;
import com.finance.portfolio.dto.request.PortfolioCreateRequest;
import com.finance.portfolio.dto.request.PositionRequest;
import com.finance.portfolio.dto.response.PortfolioResponse;
import com.finance.portfolio.repository.PortfolioRepository;
import com.finance.portfolio.service.PortfolioCrudService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Recreates the demo portfolio via the same service-layer commands a real user would issue from the
 * UI — {@link PortfolioCrudService#addPosition} for spot lots, {@link DerivativePositionService#open}
 * for VIOP — so every downstream side-effect (lot-change event, snapshot backfill, asset aggregate,
 * notifications) fires naturally. Replaces the prior {@code demo_portfolio.sql} hack, which inserted
 * raw rows with {@code session_replication_role = replica} and pinned to unstable
 * {@code tracked_asset_id} values that often pointed at the wrong instrument.
 * <p>
 * Idempotent: skips entirely once the demo user has any portfolio. Activated only when
 * {@code app.demo.enabled=true} (set by the demo Docker overlay).
 */
@Log4j2
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.demo", name = "enabled", havingValue = "true")
public class DemoSeeder {

    // Matches the demouser account in keycloak/realm-demo.json (the only stable identity the demo overlay ships).
    private static final String DEMO_USER_SUB = "22222222-2222-4222-8222-222222222222";
    private static final String DEMO_PORTFOLIO_NAME = "Demo Portföy";

    private final PortfolioRepository portfolioRepository;
    private final PortfolioCrudService portfolioCrud;
    private final DerivativePositionService derivativeService;
    private final TrackedAssetCommandService trackedAssetCommand;

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (portfolioRepository.countByUserSub(DEMO_USER_SUB) > 0) {
            log.info("Demo portfolio already exists for {}, skipping seed.", DEMO_USER_SUB);
            return;
        }
        try {
            log.info("Seeding demo portfolio for {} via service layer", DEMO_USER_SUB);
            DEMO_LOTS.forEach(lot -> ensureTracked(lot.assetType, lot.assetCode, lot.displayName));
            PortfolioResponse portfolio = portfolioCrud.createPortfolio(
                    DEMO_USER_SUB, new PortfolioCreateRequest(DEMO_PORTFOLIO_NAME));
            for (DemoLot lot : DEMO_LOTS) {
                portfolioCrud.addPosition(portfolio.id(), DEMO_USER_SUB, lot.toRequest());
            }
            for (OpenDerivativePositionRequest req : DEMO_DERIVATIVES) {
                derivativeService.open(portfolio.id(), DEMO_USER_SUB, req);
            }
            log.info("Demo portfolio seeded: {} spot lots + {} viop", DEMO_LOTS.size(), DEMO_DERIVATIVES.size());
        } catch (Exception e) {
            log.error("Demo seed failed: {}", e.getMessage(), e);
        }
    }

    private void ensureTracked(String assetType, String assetCode, String displayName) {
        TrackedAssetType type = TrackedAssetType.valueOf(assetType);
        // autoTrack is the cheapest path: it no-ops when the row exists (e.g. the scraper already
        // registered the symbol on a non-demo install) and otherwise creates a minimal row linked to
        // the underlying Instrument — no scraping is triggered, so the rate-limited TEFAS/EVDS jobs
        // are not stressed at seed time.
        trackedAssetCommand.autoTrack(type, assetCode, displayName, 0);
    }

    // VIOP demo set: a mix of LONG/SHORT, index/currency futures, open/closed — entry & exit
    // prices anchored to real viop_candles closes so the chart doesn't show synthetic jumps on
    // open day vs the next backfilled day. All TRY-quoted, contract sizes per BIST defaults
    // (index futures = 10, currency futures = 1000).
    private static final List<OpenDerivativePositionRequest> DEMO_DERIVATIVES = List.of(
            // XU030 Dec 2026 — LONG entry near February high, still open, small unrealised loss
            new OpenDerivativePositionRequest(
                    "F_XU0301226", DerivativeDirection.LONG,
                    LocalDate.of(2026, 2, 15), new BigDecimal("18500"),
                    new BigDecimal("5"), null, null),
            // XU030 Aug 2026 — SHORT entry near intraday peak (10 Mar 17000), still open
            new OpenDerivativePositionRequest(
                    "F_XU0300826", DerivativeDirection.SHORT,
                    LocalDate.of(2026, 3, 10), new BigDecimal("17000"),
                    new BigDecimal("3"), null, null),
            // USDTRY Aug 2026 — LONG currency-future hedge, opened Aug 2025 (long horizon hold)
            new OpenDerivativePositionRequest(
                    "F_USDTRY0826", DerivativeDirection.LONG,
                    LocalDate.of(2025, 8, 11), new BigDecimal("55.00"),
                    new BigDecimal("10"), null, null),
            // XU030 Jun 2026 — LONG closed at gain (Jan→Apr 2026, +18% realised)
            new OpenDerivativePositionRequest(
                    "F_XU0300626", DerivativeDirection.LONG,
                    LocalDate.of(2026, 1, 9), new BigDecimal("14779"),
                    new BigDecimal("4"),
                    LocalDate.of(2026, 4, 13), new BigDecimal("17396"))
    );

    /** A lot the demo user "opened" (and optionally "closed") via the same API a real user would call. */
    private record DemoLot(String assetType, String assetCode, String displayName,
                           BigDecimal quantity, LocalDateTime entryDate, BigDecimal entryPrice,
                           LocalDateTime exitDate, BigDecimal exitPrice) {
        PositionRequest toRequest() {
            return new PositionRequest(assetType, assetCode, quantity, entryDate, entryPrice,
                    exitDate, exitPrice);
        }
    }

    private static DemoLot open(String type, String code, String name, String qty, String date, String price) {
        return new DemoLot(type, code, name, new BigDecimal(qty),
                LocalDateTime.parse(date + "T10:00:00"), new BigDecimal(price), null, null);
    }

    private static DemoLot closed(String type, String code, String name, String qty,
                                  String entryDate, String entryPrice,
                                  String exitDate, String exitPrice) {
        return new DemoLot(type, code, name, new BigDecimal(qty),
                LocalDateTime.parse(entryDate + "T10:00:00"), new BigDecimal(entryPrice),
                LocalDateTime.parse(exitDate + "T10:00:00"), new BigDecimal(exitPrice));
    }

    // A plausible Turkish retail investor portfolio built up over 2022-2024. Entry/exit prices
    // are anchored to the actual TRY market close on each lot's transaction date (sourced from
    // stock_candles / fund_candles / crypto_candles × USD-TRY on that date). Inaccurate hardcoded
    // prices would surface as artificial step jumps in the chart on lot-add days when the next
    // day's candle close differs by ~10× (e.g. FROTO "620" vs real ~60 on 2023-04-10).
    private static final List<DemoLot> DEMO_LOTS = List.of(
            open("STOCK",  "BIMAS.IS",  "BİM Birleşik Mağazalar",       "200",  "2023-09-15", "135.00"),
            open("STOCK",  "FROTO.IS",  "Ford Otomotiv Sanayi",          "50",  "2023-04-10",  "60.76"),
            open("STOCK",  "AKBNK.IS",  "Akbank T.A.Ş.",               "1500",  "2023-06-20",  "19.04"),
            open("STOCK",  "KCHOL.IS",  "Koç Holding",                  "300",  "2023-11-08", "143.30"),
            open("STOCK",  "ASELS.IS",  "Aselsan Elektronik",           "400",  "2024-01-22",  "47.12"),
            open("STOCK",  "ISCTR.IS",  "Türkiye İş Bankası (C)",      "2000",  "2023-02-15",   "4.19"),
            open("STOCK",  "GARAN.IS",  "Garanti BBVA",                "1200",  "2023-08-03",  "45.58"),
            open("STOCK",  "SAHOL.IS",  "Sabancı Holding",              "800",  "2023-12-05",  "61.95"),
            open("FUND",   "FUS",       "QNB Portföy Amerikan Doları YBYF", "200", "2024-02-15", "2896.29"),
            open("FUND",   "MDL",       "Marmara Capital Hisse Fonu",  "5000",  "2023-03-22",  "22.00"),
            open("CRYPTO", "bitcoin",   "Bitcoin",                     "0.04",  "2024-04-10", "2260000.00"),
            open("CRYPTO", "ethereum",  "Ethereum",                     "0.6",  "2024-05-18", "99934.00"),
            open("FOREX",  "USD",       "ABD Doları",                  "5000",  "2023-01-13",  "18.79"),
            closed("STOCK", "FROTO.IS", "Ford Otomotiv Sanayi",         "100",
                    "2022-10-14",  "34.55", "2023-09-28",  "83.37"),
            closed("STOCK", "TUPRS.IS", "Tüpraş",                       "200",
                    "2022-08-01",  "39.00", "2023-11-15", "151.70"),
            closed("CRYPTO", "bitcoin", "Bitcoin",                     "0.05",
                    "2023-01-09", "322772.00", "2023-12-19", "1267000.00")
    );
}
