package com.cuevas.powerspike.dto;

import java.util.List;

public record LiveClientAllDataDTO(
        LiveClientActivePlayerDTO activePlayer,
        List<LiveClientPlayerDTO> allPlayers,
        LiveClientEventsDTO events
) {}
