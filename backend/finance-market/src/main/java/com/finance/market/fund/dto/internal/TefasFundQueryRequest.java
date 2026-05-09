package com.finance.market.fund.dto.internal;

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
