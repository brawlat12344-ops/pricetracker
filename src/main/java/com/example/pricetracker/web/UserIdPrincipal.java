package com.example.pricetracker.web;

import java.security.Principal;
import java.util.UUID;

public record UserIdPrincipal(UUID id) implements Principal {

    @Override
    public String getName() {
        return id.toString();
    }
}
