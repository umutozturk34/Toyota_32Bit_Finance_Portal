package com.finance.portfolio.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioEntityTest {

    @Test
    void portfolio_prePersist_assignsCreatedAt_whenNull() throws Exception {
        Portfolio p = new Portfolio();

        invokePrePersist(p);

        assertThat(p.getCreatedAt()).isNotNull();
    }

    @Test
    void portfolio_prePersist_preservesCreatedAt_whenAlreadySet() throws Exception {
        Portfolio p = new Portfolio();
        LocalDateTime original = LocalDateTime.of(2020, 1, 1, 0, 0);
        setField(p, "createdAt", original);

        invokePrePersist(p);

        assertThat(p.getCreatedAt()).isEqualTo(original);
    }

    @Test
    void portfolioDailySnapshot_prePersist_assignsCreatedAt_whenNull() throws Exception {
        PortfolioDailySnapshot s = new PortfolioDailySnapshot();

        invokePrePersist(s);

        assertThat(s.getCreatedAt()).isNotNull();
    }

    @Test
    void portfolioDailySnapshot_prePersist_preservesCreatedAt_whenAlreadySet() throws Exception {
        PortfolioDailySnapshot s = new PortfolioDailySnapshot();
        LocalDateTime original = LocalDateTime.of(2020, 1, 1, 0, 0);
        s.setCreatedAt(original);

        invokePrePersist(s);

        assertThat(s.getCreatedAt()).isEqualTo(original);
    }

    @Test
    void portfolioAssetDailySnapshot_prePersist_assignsCreatedAt() throws Exception {
        PortfolioAssetDailySnapshot s = new PortfolioAssetDailySnapshot();

        invokePrePersist(s);

        assertThat(s.getCreatedAt()).isNotNull();
    }

    private void invokePrePersist(Object entity) throws Exception {
        invokePackagePrivate(entity, "prePersist");
    }

    private void invokePackagePrivate(Object entity, String methodName) throws Exception {
        Method method = entity.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(entity);
    }

    private void setField(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
