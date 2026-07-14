package com.cuevas.powerspike.dto;

import java.util.List;

public record LiveClientEventsDTO(
        List<LiveClientEventDTO> Events
) {}
