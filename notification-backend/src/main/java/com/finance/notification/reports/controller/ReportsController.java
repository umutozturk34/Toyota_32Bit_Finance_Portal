package com.finance.notification.reports.controller;

import com.finance.notification.portfolio.PortfolioOwnershipReader;
import com.finance.notification.reports.dto.PortfolioPdfRequest;
import com.finance.notification.reports.service.PortfolioPdfService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Generates and streams a portfolio PDF report as a file download. Verifies the caller owns the
 * requested portfolio (else 403) and forwards the caller's token so report data is fetched as them.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportsController {

    private static final DateTimeFormatter FILENAME_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final PortfolioPdfService service;
    private final PortfolioOwnershipReader ownershipReader;

    @PostMapping(value = "/portfolio-pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> portfolioPdf(@Valid @RequestBody PortfolioPdfRequest request,
                                               @AuthenticationPrincipal Jwt jwt) {
        String userSub = jwt.getSubject();
        if (!ownershipReader.isOwner(request.portfolioId(), userSub)) {
            throw new AccessDeniedException("portfolio not owned by caller");
        }

        byte[] pdf = service.generate(request, userSub, jwt.getTokenValue());
        // Filename uses the portfolio NAME (slugified), never the numeric DB id — the id must not leak.
        String slug = slugify(request.portfolioName());
        String filename = (slug.isEmpty() ? "portfolio" : slug) + "-"
                + LocalDate.now().format(FILENAME_DATE) + ".pdf";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(pdf.length);
        return new ResponseEntity<>(pdf, headers, 200);
    }

    /** ASCII filename slug from a (possibly Turkish) portfolio name; empty if nothing usable remains. */
    private static String slugify(String name) {
        if (name == null) return "";
        String t = name.trim()
                .replace("ı", "i").replace("İ", "i")
                .replace("ş", "s").replace("Ş", "s")
                .replace("ç", "c").replace("Ç", "c")
                .replace("ö", "o").replace("Ö", "o")
                .replace("ü", "u").replace("Ü", "u")
                .replace("ğ", "g").replace("Ğ", "g")
                .toLowerCase(java.util.Locale.ENGLISH)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(?:^-++)|(?:-++$)", "");
        return t.length() > 40 ? t.substring(0, 40) : t;
    }
}
