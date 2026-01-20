package com.finance.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BistIndexDto {
    private String name;           // "BIST 100"
    private Double current;        // Güncel değer
    private Double changeRate;     // Değişim yüzdesi
    private Double min;            // Gün içi en düşük
    private Double max;            // Gün içi en yüksek
    private Double opening;        // Açılış
    private Double closing;        // Kapanış
    private String time;           // Güncelleme saati
    private String date;           // Tarih
}
