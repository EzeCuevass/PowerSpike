package com.cuevas.powerspike.dto;

public record LcuWsEventDTO(
        int messageType,
        String eventName,
        LcuWsEventDataDTO data
) {}
