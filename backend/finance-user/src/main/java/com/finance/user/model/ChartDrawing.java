package com.finance.user.model;
import com.finance.common.model.*;
import com.finance.common.model.value.*;
import com.finance.common.dto.*;
import com.finance.common.dto.external.*;
import com.finance.common.dto.internal.*;
import com.finance.common.dto.request.*;
import com.finance.common.dto.response.*;
import com.finance.common.exception.*;
import com.finance.common.util.*;
import com.finance.common.service.*;
import com.finance.common.service.assetpricing.*;
import com.finance.common.config.*;
import com.finance.common.filter.*;
import com.finance.common.filter.tier.*;
import com.finance.common.scheduler.*;
import com.finance.common.event.*;
import com.finance.common.mapper.*;
import com.finance.common.repository.*;
import com.finance.common.client.*;

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
