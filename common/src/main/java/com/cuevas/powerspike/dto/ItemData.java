package com.cuevas.powerspike.dto;

import java.util.Map;

public record ItemData(Map<String, ItemInfo> data) {
    public record ItemInfo(String name, String description) {}
}