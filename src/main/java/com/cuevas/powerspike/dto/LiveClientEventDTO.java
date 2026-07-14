package com.cuevas.powerspike.dto;

import java.util.List;

public record LiveClientEventDTO(
        int EventID,
        String EventName,
        double EventTime,
        String KillerName,
        String VictimName,
        List<String> Assisters
) {}
