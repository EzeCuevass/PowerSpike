package com.cuevas.powerspike.dto;

import java.util.List;

public record LcuBansDTO(
        List<LcuBanDTO> myTeamBans,
        List<LcuBanDTO> theirTeamBans,
        int numBans
) {}
