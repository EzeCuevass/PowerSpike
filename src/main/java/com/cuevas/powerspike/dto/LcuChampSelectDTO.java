package com.cuevas.powerspike.dto;

import java.util.List;

public record LcuChampSelectDTO(
        LcuTimerDTO timer,
        List<LcuTeamMemberDTO> myTeam,
        List<LcuTeamMemberDTO> theirTeam,
        LcuBansDTO bans,
        long gameId,
        String id,
        boolean isCustomGame,
        int queueId
) {}
