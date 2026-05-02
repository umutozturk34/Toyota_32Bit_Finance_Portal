package com.finance.backend.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_layouts")
public class UserLayout {

    @Id
    private String userSub;

    @Field("overview")
    private JsonNode overview;

    @Field("updated_at")
    private Instant updatedAt;

    public static UserLayout emptyFor(String userSub) {
        return UserLayout.builder()
                .userSub(userSub)
                .overview(JsonNodeFactory.instance.objectNode())
                .updatedAt(Instant.now())
                .build();
    }
}
