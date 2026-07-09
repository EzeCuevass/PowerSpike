package com.cuevas.powerspike.dto;

import java.util.Map;

public record ChampionData(
        Map<String, ChampionInfo> data
) {}
