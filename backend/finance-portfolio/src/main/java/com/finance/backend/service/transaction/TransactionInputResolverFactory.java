package com.finance.backend.service.transaction;

import com.finance.backend.exception.BadRequestException;
import com.finance.backend.model.AssetType;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.List;

@Log4j2
@Component
@RequiredArgsConstructor
public class TransactionInputResolverFactory {

    private final List<TransactionInputResolver> resolvers;

    public TransactionInputResolver getResolver(AssetType assetType) {
        return resolvers.stream()
                .filter(r -> r.supports(assetType))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("No resolver for asset type: " + assetType));
    }
}
