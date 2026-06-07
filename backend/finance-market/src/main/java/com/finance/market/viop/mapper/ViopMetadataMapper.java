package com.finance.market.viop.mapper;

import com.finance.market.viop.dto.ViopContractSpec;
import com.finance.market.viop.dto.external.ViopFutureMetadataDto;
import com.finance.market.viop.dto.external.ViopOptionMetadataDto;
import com.finance.market.viop.model.ViopOptionSide;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Converts İş Yatırım future/option metadata DTOs into {@link ViopContractSpec}s, parsing
 * Turkish-formatted dates and decimals leniently (null on failure).
 */
@Log4j2
@Component
public class ViopMetadataMapper {

    private static final DateTimeFormatter EXPIRY_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", new Locale.Builder().setLanguage("tr").build());

    /**
     * Builds a fully specified futures {@link ViopContractSpec} from an İş Yatırım future metadata
     * record, deriving a display name from the underlying and expiry and parsing the Turkish-formatted
     * expiry, contract size and initial margin (each null when unparseable).
     */
    public ViopContractSpec toFutureSpec(ViopFutureMetadataDto dto) {
        return ViopContractSpec.future(
                dto.symbol(),
                buildFutureDisplayName(dto),
                dto.underlying(),
                parseExpiry(dto.expiryDate()),
                parseTurkishDecimal(dto.contractSize()),
                parseTurkishDecimal(dto.initialMargin()),
                dto.settlementType(),
                dto.currency()
        );
    }

    /**
     * Builds an option template {@link ViopContractSpec} from option metadata. The result is a
     * template, not a tradable contract: expiry, margin and strike are left null (filled in per
     * instrument elsewhere). The underlying code falls back to the raw underlying when blank, the
     * contract size is extracted from the free-text size description, and the side is parsed from
     * the option type.
     */
    public ViopContractSpec toOptionTemplateSpec(ViopOptionMetadataDto dto) {
        return ViopContractSpec.option(
                dto.sampleCode(),
                dto.underlyingDisplay(),
                dto.underlyingCode() != null && !dto.underlyingCode().isBlank() ? dto.underlyingCode() : dto.underlying(),
                null,
                parseContractSizeFromDescription(dto.contractSize()),
                null,
                dto.settlementMethod(),
                dto.currency(),
                parseSide(dto.optionType()),
                null,
                null
        );
    }

    private String buildFutureDisplayName(ViopFutureMetadataDto dto) {
        return dto.underlying() + " " + dto.expiryDate().substring(0, 10);
    }

    private LocalDate parseExpiry(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw, EXPIRY_FMT);
        } catch (Exception e) {
            log.debug("Unparseable VIOP expiry raw={}", raw);
            return null;
        }
    }

    private BigDecimal parseTurkishDecimal(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return new BigDecimal(raw.replace(".", "").replace(",", "."));
        } catch (NumberFormatException e) {
            log.debug("Unparseable VIOP metadata decimal raw={}", raw);
            return null;
        }
    }

    /** Extracts the leading numeric token from a free-text contract-size description. */
    private BigDecimal parseContractSizeFromDescription(String desc) {
        if (desc == null) return null;
        StringBuilder digits = new StringBuilder();
        for (char c : desc.toCharArray()) {
            if (Character.isDigit(c) || c == '.' || c == ',') digits.append(c);
            else if (digits.length() > 0) break;
        }
        return parseTurkishDecimal(digits.toString());
    }

    private ViopOptionSide parseSide(String raw) {
        if (raw == null) return null;
        return raw.trim().equalsIgnoreCase("CALL") ? ViopOptionSide.CALL : ViopOptionSide.PUT;
    }
}
