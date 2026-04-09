package com.finance.backend.service.transaction;

import com.finance.backend.model.AssetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TransactionInputResolverFactory {

    private final List<TransactionInputResolver> resolvers;

    public TransactionInputResolver getResolver(AssetType assetType) {
        return resolvers.stream()
                .filter(r -> r.supports(assetType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No resolver for asset type: " + assetType));
    }
}
