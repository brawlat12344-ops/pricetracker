package com.example.pricetracker.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

public final class AuthCookie {

    public static final String NAME = "pricetracker_token";

    private AuthCookie() {
    }

    public static void set(HttpServletResponse response, String token, int maxAgeSeconds) {
        Cookie cookie = new Cookie(NAME, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // включить true при HTTPS в проде
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    public static void clear(HttpServletResponse response) {
        Cookie cookie = new Cookie(NAME, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }
}
