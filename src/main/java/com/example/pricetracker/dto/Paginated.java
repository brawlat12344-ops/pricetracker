package com.example.pricetracker.dto;

import java.util.List;

public record Paginated<T>(List<T> items, long total, int page, int pageSize) {
}
