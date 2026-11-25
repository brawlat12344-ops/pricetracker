package com.example.pricetracker.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType) {

    public static TokenResponse of(String token) {
        return new TokenResponse(token, "bearer");
    }
}
