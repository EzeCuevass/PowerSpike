package com.cuevas.powerspike.dto;

import java.util.List;

public record Perks(
        List<Long> perkIds,
        long perkStyle,
        long perkSubStyle
) {}
