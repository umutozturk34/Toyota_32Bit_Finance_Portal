package com.finance.notification.reports.controller;

import com.finance.notification.reports.dto.PortfolioPdfRequest;
import com.finance.notification.reports.service.PortfolioPdfService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportsController {

    private static final DateTimeFormatter FILENAME_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final PortfolioPdfService service;

    @PostMapping(value = "/portfolio-pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> portfolioPdf(@Valid @RequestBody PortfolioPdfRequest request) {
        byte[] pdf = service.generate(request);
        String slug = slugify(request.portfolio().name());
        String filename = "portfolio-" + slug + "-" + LocalDate.now().format(FILENAME_DATE) + ".pdf";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(pdf.length);
        return new ResponseEntity<>(pdf, headers, 200);
    }

    private String slugify(String s) {
        if (s == null) return "report";
        String trimmed = s.trim().toLowerCase()
                .replaceAll("[çÇ]", "c").replaceAll("[ğĞ]", "g")
                .replaceAll("[ıİ]", "i").replaceAll("[öÖ]", "o")
                .replaceAll("[şŞ]", "s").replaceAll("[üÜ]", "u")
                .replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return trimmed.isEmpty() ? "report" : trimmed;
    }
}
