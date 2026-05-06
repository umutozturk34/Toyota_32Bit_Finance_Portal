package com.finance.common.model.value;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.math.BigDecimal;

@Converter(autoApply = false)
public class MoneyTRYConverter implements AttributeConverter<MoneyTRY, BigDecimal> {

    @Override
    public BigDecimal convertToDatabaseColumn(MoneyTRY money) {
        return money == null ? null : money.amount();
    }

    @Override
    public MoneyTRY convertToEntityAttribute(BigDecimal amount) {
        return amount == null ? null : MoneyTRY.of(amount);
    }
}
