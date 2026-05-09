package com.finance.news.util;

import com.finance.news.model.NewsCategory;

import java.util.List;
import java.util.Map;

public final class NewsCategoryResolverConfig {

    private NewsCategoryResolverConfig() {
    }

    public record ResolverConfig(
            Map<NewsCategory, List<String>> categoryKeywords,
            RuleKeywords ruleKeywords,
            List<NewsCategory> summaryDiversityCategories
    ) {
    }

    public static class RawResolverConfig {
        public Map<String, List<String>> categoryKeywords;
        public RuleKeywords ruleKeywords;
        public List<String> summaryDiversityCategories;
    }

    public static class RuleKeywords {
        public List<String> summaryHint;
        public List<String> generalMarketBasket;
        public List<String> nonParityMarketAnchors;
        public List<String> absoluteCrypto;
        public List<String> absoluteParity;
        public List<String> parityPriority;
        public List<String> bondPriority;
        public List<String> bondContext;
        public List<String> macroPolicy;
        public List<String> strongCompanyNews;
        public List<String> definiteCorporate;
        public List<String> foreignBondContext;

        public RuleKeywords() {
        }

        public RuleKeywords(
                List<String> summaryHint,
                List<String> generalMarketBasket,
                List<String> nonParityMarketAnchors,
                List<String> absoluteCrypto,
                List<String> absoluteParity,
                List<String> parityPriority,
                List<String> bondPriority,
                List<String> bondContext,
                List<String> macroPolicy,
                List<String> strongCompanyNews,
                List<String> definiteCorporate,
                List<String> foreignBondContext
        ) {
            this.summaryHint = summaryHint;
            this.generalMarketBasket = generalMarketBasket;
            this.nonParityMarketAnchors = nonParityMarketAnchors;
            this.absoluteCrypto = absoluteCrypto;
            this.absoluteParity = absoluteParity;
            this.parityPriority = parityPriority;
            this.bondPriority = bondPriority;
            this.bondContext = bondContext;
            this.macroPolicy = macroPolicy;
            this.strongCompanyNews = strongCompanyNews;
            this.definiteCorporate = definiteCorporate;
            this.foreignBondContext = foreignBondContext;
        }

        public List<String> summaryHint() {
            return summaryHint;
        }

        public List<String> generalMarketBasket() {
            return generalMarketBasket;
        }

        public List<String> nonParityMarketAnchors() {
            return nonParityMarketAnchors;
        }

        public List<String> absoluteCrypto() {
            return absoluteCrypto;
        }

        public List<String> absoluteParity() {
            return absoluteParity;
        }

        public List<String> parityPriority() {
            return parityPriority;
        }

        public List<String> bondPriority() {
            return bondPriority;
        }

        public List<String> bondContext() {
            return bondContext;
        }

        public List<String> macroPolicy() {
            return macroPolicy;
        }

        public List<String> strongCompanyNews() {
            return strongCompanyNews;
        }

        public List<String> definiteCorporate() {
            return definiteCorporate;
        }

        public List<String> foreignBondContext() {
            return foreignBondContext;
        }
    }
}
