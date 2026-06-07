package com.finance.notification.reports.service;

import com.finance.notification.reports.dto.PerformanceSeriesPoint;
import com.finance.notification.reports.model.ReportPalette;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Builds self-contained SVG charts embedded in the portfolio PDF: a performance line chart with
 * gridlines and date axis (line/fill colored green or red by overall trend), and an allocation donut.
 * Colors come from the theme {@link ReportPalette}. Tiny donut slices are floored to a minimum
 * visible fraction (with larger slices shrunk to compensate) so labels stay readable, while true
 * percentages are still shown.
 */
@Service
public class ReportSvgService {

    private static final int W = 760;
    private static final int H = 160;
    private static final int PAD_LEFT = 50;
    private static final int PAD_RIGHT = 14;
    private static final int PAD_TOP = 14;
    private static final int PAD_BOTTOM = 26;
    private static final int Y_TICKS = 4;
    private static final int X_TICKS = 6;
    private static final DateTimeFormatter X_LABEL_FMT = DateTimeFormatter.ofPattern("dd MMM");
    private static final DateTimeFormatter X_LABEL_MULTI_YEAR_FMT = DateTimeFormatter.ofPattern("MMM yy");

    /**
     * Renders the value-over-time line chart; returns an empty placeholder when fewer than two points
     * exist. The y-axis value tick labels are prefixed with {@code currencySymbol} (e.g. "$1.2M");
     * pass an empty string for percentage axes (e.g. the cumulative-return chart) so no symbol shows.
     */
    public String performanceLineChart(List<PerformanceSeriesPoint> points, ReportPalette palette,
                                        Locale locale, String currencySymbol) {
        if (points == null || points.size() < 2) {
            return emptyPlaceholder(palette);
        }
        String symbol = currencySymbol == null ? "" : currencySymbol;

        double minV = Double.POSITIVE_INFINITY;
        double maxV = Double.NEGATIVE_INFINITY;
        long minT = Long.MAX_VALUE;
        long maxT = Long.MIN_VALUE;
        for (PerformanceSeriesPoint p : points) {
            double v = p.value();
            if (v < minV) minV = v;
            if (v > maxV) maxV = v;
            long t = epoch(p.timestamp());
            if (t < minT) minT = t;
            if (t > maxT) maxT = t;
        }
        if (maxV == minV) maxV = minV + 1;
        if (maxT == minT) maxT = minT + 1;
        double range = maxV - minV;
        double yPad = range * 0.08;
        double yMin = minV - yPad;
        double yMax = maxV + yPad;

        int plotW = W - PAD_LEFT - PAD_RIGHT;
        int plotH = H - PAD_TOP - PAD_BOTTOM;

        StringBuilder linePath = new StringBuilder();
        StringBuilder areaPath = new StringBuilder();
        for (int i = 0; i < points.size(); i++) {
            PerformanceSeriesPoint p = points.get(i);
            double x = PAD_LEFT + ((epoch(p.timestamp()) - minT) / (double) (maxT - minT)) * plotW;
            double y = PAD_TOP + (1.0 - (p.value() - yMin) / (yMax - yMin)) * plotH;
            if (i == 0) {
                linePath.append("M").append(fmt(x)).append(",").append(fmt(y));
                areaPath.append("M").append(fmt(x)).append(",").append(fmt(PAD_TOP + plotH))
                        .append(" L").append(fmt(x)).append(",").append(fmt(y));
            } else {
                linePath.append(" L").append(fmt(x)).append(",").append(fmt(y));
                areaPath.append(" L").append(fmt(x)).append(",").append(fmt(y));
            }
        }
        double lastX = PAD_LEFT + plotW;
        areaPath.append(" L").append(fmt(lastX)).append(",").append(fmt(PAD_TOP + plotH)).append(" Z");

        boolean positiveTrend = points.get(points.size() - 1).value() >= points.get(0).value();
        String lineColor = positiveTrend ? palette.successFg() : palette.dangerFg();
        String fillColor = positiveTrend ? palette.successFg() : palette.dangerFg();

        StringBuilder svg = new StringBuilder(4096);
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                .append(W).append(" ").append(H)
                .append("\" preserveAspectRatio=\"xMidYMid meet\" style=\"display:block;width:100%;height:auto\">");
        svg.append("<defs><linearGradient id=\"perfFill\" x1=\"0\" y1=\"0\" x2=\"0\" y2=\"1\">")
                .append("<stop offset=\"0\" stop-color=\"").append(fillColor).append("\" stop-opacity=\"0.30\"/>")
                .append("<stop offset=\"1\" stop-color=\"").append(fillColor).append("\" stop-opacity=\"0\"/>")
                .append("</linearGradient></defs>");

        for (int i = 0; i < Y_TICKS; i++) {
            double tickV = yMin + (yMax - yMin) * (i / (double) (Y_TICKS - 1));
            double y = PAD_TOP + (1.0 - (tickV - yMin) / (yMax - yMin)) * plotH;
            svg.append("<line x1=\"").append(PAD_LEFT).append("\" y1=\"").append(fmt(y))
                    .append("\" x2=\"").append(PAD_LEFT + plotW).append("\" y2=\"").append(fmt(y))
                    .append("\" stroke=\"").append(palette.border())
                    .append("\" stroke-dasharray=\"2,4\" stroke-width=\"0.6\"/>");
            svg.append("<text x=\"").append(PAD_LEFT - 8).append("\" y=\"").append(fmt(y + 3))
                    .append("\" text-anchor=\"end\" fill=\"").append(palette.subtle())
                    .append("\" font-size=\"9\" font-family=\"'Fira Code', monospace\">")
                    .append(formatTickValue(tickV, symbol)).append("</text>");
        }

        long tRange = maxT - minT;
        LocalDateTime first = LocalDateTime.ofEpochSecond(minT, 0, ZoneOffset.UTC);
        LocalDateTime last = LocalDateTime.ofEpochSecond(maxT, 0, ZoneOffset.UTC);
        boolean multiYear = first.getYear() != last.getYear();
        DateTimeFormatter xFmt = (multiYear ? X_LABEL_MULTI_YEAR_FMT : X_LABEL_FMT).withLocale(locale);
        String prevXLabel = null;
        for (int i = 0; i < X_TICKS; i++) {
            double tT = minT + (tRange) * (i / (double) (X_TICKS - 1));
            double x = PAD_LEFT + ((tT - minT) / (double) tRange) * plotW;
            LocalDateTime dt = LocalDateTime.ofEpochSecond((long) tT, 0, ZoneOffset.UTC);
            String label = xFmt.format(dt);
            // Short windows make adjacent ticks resolve to the same label (e.g. 6 ticks all "Jun 26");
            // skip the duplicate so the axis doesn't repeat the same month/day across the row.
            if (label.equals(prevXLabel)) continue;
            prevXLabel = label;
            svg.append("<text x=\"").append(fmt(x)).append("\" y=\"").append(H - PAD_BOTTOM + 18)
                    .append("\" text-anchor=\"middle\" fill=\"").append(palette.subtle())
                    .append("\" font-size=\"9\" font-family=\"'Fira Code', monospace\">")
                    .append(label).append("</text>");
        }

        svg.append("<path d=\"").append(areaPath).append("\" fill=\"url(#perfFill)\" stroke=\"none\"/>");
        svg.append("<path d=\"").append(linePath).append("\" fill=\"none\" stroke=\"").append(lineColor)
                .append("\" stroke-width=\"1.8\" stroke-linejoin=\"round\" stroke-linecap=\"round\"/>");

        svg.append("</svg>");
        return svg.toString();
    }

    /** Renders the allocation donut; returns an empty placeholder when there is no positive allocation. */
    public String allocationDonut(List<com.finance.notification.reports.view.AllocationViewItem> items, ReportPalette palette) {
        if (items == null || items.isEmpty()) return emptyPlaceholder(palette);
        double total = items.stream()
                .mapToDouble(i -> i.value() == null ? 0d : i.value().doubleValue())
                .sum();
        if (total <= 0) return emptyPlaceholder(palette);

        int size = 180;
        double cx = size / 2.0;
        double cy = size / 2.0;
        double rOuter = 78;
        double rInner = 46;

        StringBuilder svg = new StringBuilder(2048);
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(size).append(" ").append(size)
                .append("\" preserveAspectRatio=\"xMidYMid meet\" style=\"display:block;width:100%;max-width:180px;height:auto\">");

        final double MIN_VISIBLE_FRAC = 0.012;
        long nonZeroCount = items.stream().filter(i -> i.value() != null && i.value().doubleValue() > 0).count();
        double labelRadius = (rOuter + rInner) / 2.0;
        double[] displayFracs = new double[items.size()];
        double[] realFracs = new double[items.size()];
        double excessFromFloor = 0d;
        double bigPool = 0d;
        for (int i = 0; i < items.size(); i++) {
            double v = items.get(i).value() == null ? 0d : items.get(i).value().doubleValue();
            double f = v / total;
            realFracs[i] = f;
            if (f <= 0) {
                displayFracs[i] = 0d;
            } else if (f < MIN_VISIBLE_FRAC) {
                displayFracs[i] = MIN_VISIBLE_FRAC;
                excessFromFloor += MIN_VISIBLE_FRAC - f;
            } else {
                displayFracs[i] = f;
                bigPool += f;
            }
        }
        if (excessFromFloor > 0 && bigPool > 0) {
            double shrinkFactor = (bigPool - excessFromFloor) / bigPool;
            for (int i = 0; i < items.size(); i++) {
                if (realFracs[i] >= MIN_VISIBLE_FRAC) {
                    displayFracs[i] = realFracs[i] * shrinkFactor;
                }
            }
        }
        double angle = -Math.PI / 2.0;
        for (int i = 0; i < items.size(); i++) {
            double frac = displayFracs[i];
            if (frac <= 0) continue;
            double next = angle + frac * 2 * Math.PI;
            if (frac >= 0.9999) {
                // A 360° arc is degenerate (start point == end point) and renders nothing, so draw
                // the full donut ring as a thick-stroked circle instead.
                double rMid = (rOuter + rInner) / 2.0;
                svg.append("<circle cx=\"").append(fmt(cx)).append("\" cy=\"").append(fmt(cy))
                        .append("\" r=\"").append(fmt(rMid)).append("\" fill=\"none\" stroke=\"")
                        .append(items.get(i).color()).append("\" stroke-width=\"")
                        .append(fmt(rOuter - rInner)).append("\"/>");
                angle = next;
                continue;
            }
            int largeArc = frac > 0.5 ? 1 : 0;
            double x1o = cx + rOuter * Math.cos(angle);
            double y1o = cy + rOuter * Math.sin(angle);
            double x2o = cx + rOuter * Math.cos(next);
            double y2o = cy + rOuter * Math.sin(next);
            double x1i = cx + rInner * Math.cos(angle);
            double y1i = cy + rInner * Math.sin(angle);
            double x2i = cx + rInner * Math.cos(next);
            double y2i = cy + rInner * Math.sin(next);

            svg.append("<path d=\"")
                    .append("M ").append(fmt(x1o)).append(",").append(fmt(y1o))
                    .append(" A ").append(rOuter).append(",").append(rOuter).append(" 0 ").append(largeArc).append(" 1 ")
                    .append(fmt(x2o)).append(",").append(fmt(y2o))
                    .append(" L ").append(fmt(x2i)).append(",").append(fmt(y2i))
                    .append(" A ").append(rInner).append(",").append(rInner).append(" 0 ").append(largeArc).append(" 0 ")
                    .append(fmt(x1i)).append(",").append(fmt(y1i))
                    .append(" Z\" fill=\"").append(items.get(i).color())
                    .append("\" stroke=\"").append(palette.card()).append("\" stroke-width=\"1.5\"/>");
            angle = next;
        }
        if (nonZeroCount == 1) {
            svg.append("<text x=\"").append(fmt(cx)).append("\" y=\"").append(fmt(cy + 5))
                    .append("\" text-anchor=\"middle\" fill=\"").append(palette.fg())
                    .append("\" font-size=\"18\" font-weight=\"700\" font-family=\"'Lato', sans-serif\">100%</text>");
        } else {
            angle = -Math.PI / 2.0;
            for (int i = 0; i < items.size(); i++) {
                double displayFrac = displayFracs[i];
                if (displayFrac <= 0) { continue; }
                double next = angle + displayFrac * 2 * Math.PI;
                String pct = String.format(Locale.ROOT, "%.1f%%", realFracs[i] * 100).replace('.', ',');
                double mid = (angle + next) / 2.0;
                if (realFracs[i] >= 0.06) {
                    double tx = cx + labelRadius * Math.cos(mid);
                    double ty = cy + labelRadius * Math.sin(mid);
                    svg.append("<text x=\"").append(fmt(tx)).append("\" y=\"").append(fmt(ty + 3))
                            .append("\" text-anchor=\"middle\" fill=\"#ffffff\"")
                            .append(" font-size=\"9\" font-weight=\"700\" font-family=\"'Lato', sans-serif\">")
                            .append(pct).append("</text>");
                }
                // Sub-6% slices get NO on-donut label: several adjacent tiny slices (e.g. <1% each) placed
                // their leader labels at nearly the same angle and collided into an unreadable cluster. The
                // legend table beside the donut already lists every slice's exact percentage and value.
                angle = next;
            }
        }
        svg.append("</svg>");
        return svg.toString();
    }

    private String emptyPlaceholder(ReportPalette palette) {
        return "<div style=\"display:flex;align-items:center;justify-content:center;height:160px;color:"
                + palette.muted() + ";font-size:11px;\">—</div>";
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }

    private static String formatTickValue(double v, String prefix) {
        double abs = Math.abs(v);
        String body;
        if (abs >= 1_000_000_000d) body = String.format(Locale.ROOT, "%.1fB", v / 1_000_000_000d);
        else if (abs >= 1_000_000d) body = String.format(Locale.ROOT, "%.1fM", v / 1_000_000d);
        else if (abs >= 1_000d) body = String.format(Locale.ROOT, "%.1fK", v / 1_000d);
        else body = String.format(Locale.ROOT, "%.0f", v);
        // A leading minus must precede the currency symbol (e.g. "-$1.2M"), not sit between them.
        if (!prefix.isEmpty() && body.startsWith("-")) return "-" + prefix + body.substring(1);
        return prefix + body;
    }

    private static long epoch(LocalDateTime dt) {
        return dt.toEpochSecond(ZoneOffset.UTC);
    }
}
