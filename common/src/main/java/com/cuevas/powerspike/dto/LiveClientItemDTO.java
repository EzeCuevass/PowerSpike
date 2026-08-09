package com.cuevas.powerspike.dto;

public record LiveClientItemDTO(int itemID, String displayName, int slot, int count, boolean canUse, int price) {}
