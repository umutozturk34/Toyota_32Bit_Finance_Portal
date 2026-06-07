package com.finance.market.fund.dto.internal;

/**
 * Request body sent to the TEFAS query endpoint. Field names intentionally retain the
 * Turkish keys the upstream API expects (fund type/code, free-text search, category and
 * group codes, date range, pagination bounds {@code basSira}/{@code bitSira}, language,
 * and founder code), so they serialize directly to the wire contract without remapping.
 */
public record TefasFundQueryRequest(
        String fonTipi,
        String fonKodu,
        String aramaMetni,
        String fonTurKod,
        String fonGrubu,
        String sfonTurKod,
        String basTarih,
        String bitTarih,
        int basSira,
        int bitSira,
        String fonTurAciklama,
        String dil,
        String kurucuKod
) {
}
