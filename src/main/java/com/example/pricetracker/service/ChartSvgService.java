package com.example.pricetracker.service;

import com.example.pricetracker.dto.StatsPoint;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/** Builds the price chart as plain SVG on the server - no JavaScript needed. */
@Service
public class ChartSvgService {

    private static final int WIDTH = 800;
    private static final int HEIGHT = 300;
    private static final int PAD = 44;

    public String render(List<StatsPoint> points) {
        if (points.isEmpty()) {
            return "<p class='muted'>Пока нет снимков цены. Нажмите «Опросить» или подождите тик worker.</p>";
        }

        BigDecimal min = points.stream().map(StatsPoint::minPrice).min(BigDecimal::compareTo).orElseThrow();
        BigDecimal max = points.stream().map(StatsPoint::maxPrice).max(BigDecimal::compareTo).orElseThrow();
        BigDecimal span = max.subtract(min);
        if (span.compareTo(BigDecimal.ONE) < 0) {
            span = BigDecimal.ONE;
        }
        BigDecimal low = min.subtract(span.multiply(BigDecimal.valueOf(0.1)));
        BigDecimal high = max.add(span.multiply(BigDecimal.valueOf(0.1)));

        StringBuilder svg = new StringBuilder();
        svg.append("<svg class='chart' viewBox='0 0 ").append(WIDTH).append(' ').append(HEIGHT)
                .append("' xmlns='http://www.w3.org/2000/svg' role='img' aria-label='График цены'>");

        // Grid and Y labels
        for (int i = 0; i <= 4; i++) {
            int y = PAD + (HEIGHT - 2 * PAD) * i / 4;
            double value = high.doubleValue() - (high.doubleValue() - low.doubleValue()) * i / 4.0;
            svg.append("<line x1='").append(PAD).append("' y1='").append(y)
                    .append("' x2='").append(WIDTH - PAD).append("' y2='").append(y)
                    .append("' stroke='#334155' stroke-dasharray='3 3'/>");
            svg.append("<text x='4' y='").append(y + 4).append("' fill='#94a3b8' font-size='11'>")
                    .append(String.format(Locale.ROOT, "%.0f", value)).append("</text>");
        }

        int n = points.size();
        double usableWidth = WIDTH - 2 * PAD;
        double usableHeight = HEIGHT - 2 * PAD;
        double range = high.doubleValue() - low.doubleValue();

        svg.append(polyline(points, StatsPoint::minPrice, low, range, usableWidth, usableHeight, n,
                "#38bdf8", "2"));
        svg.append(polyline(points, StatsPoint::maxPrice, low, range, usableWidth, usableHeight, n,
                "#f87171", "2"));
        svg.append(polyline(points, StatsPoint::avgPrice, low, range, usableWidth, usableHeight, n,
                "#4ade80", "3"));

        // X labels
        for (int i = 0; i < n; i++) {
            int x = PAD + (int) (usableWidth * (n == 1 ? 0.5 : (double) i / (n - 1)));
            svg.append("<text x='").append(x).append("' y='").append(HEIGHT - 12)
                    .append("' fill='#94a3b8' font-size='11' text-anchor='middle'>")
                    .append(points.get(i).day()).append("</text>");
        }

        svg.append("<text x='").append(PAD).append("' y='16' fill='#94a3b8' font-size='12'>")
                .append("мин / макс / средняя по дням, ₽</text>");
        svg.append("</svg>");
        return svg.toString();
    }

    private String polyline(List<StatsPoint> points,
                            java.util.function.Function<StatsPoint, BigDecimal> extractor,
                            BigDecimal low, double range, double usableWidth, double usableHeight,
                            int n, String color, String width) {
        StringBuilder sb = new StringBuilder("<polyline fill='none' stroke='").append(color)
                .append("' stroke-width='").append(width).append("' points='");
        for (int i = 0; i < n; i++) {
            double x = PAD + usableWidth * (n == 1 ? 0.5 : (double) i / (n - 1));
            double value = extractor.apply(points.get(i)).doubleValue();
            double y = PAD + usableHeight * (1 - (value - low.doubleValue()) / range);
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format(Locale.ROOT, "%.1f,%.1f", x, y));
        }
        return sb.append("'/>").toString();
    }
}
