package com.finance.user.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chart_drawings")
@CompoundIndex(name = "user_market_asset", def = "{'user_sub': 1, 'market_type': 1, 'asset_code': 1}", unique = true)
public class ChartDrawing {

    @Id
    private String id;

    @Field("user_sub")
    private String userSub;

    @Field("market_type")
    private String marketType;

    @Field("asset_code")
    private String assetCode;

    @Field("drawings")
    private JsonNode drawings;

    @Field("updated_at")
    private Instant updatedAt;

    public static String compositeId(String userSub, String marketType, String assetCode) {
        return userSub + ":" + marketType + ":" + assetCode;
    }
}
