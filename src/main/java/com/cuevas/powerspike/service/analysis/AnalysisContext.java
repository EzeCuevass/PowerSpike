package com.cuevas.powerspike.service.analysis;

import com.cuevas.powerspike.dto.LcuChampSelectDTO;
import com.cuevas.powerspike.dto.LiveClientAllDataDTO;
import com.cuevas.powerspike.dto.LiveClientEventDTO;

public record AnalysisContext(
    AnalysisTrigger trigger,
    LcuChampSelectDTO champSelect,
    LiveClientAllDataDTO liveGameData,
    LiveClientEventDTO deathEvent,
    String myChampion,
    String myRole,
    String enemyChampion,
    String enemyRole,
    int gameMinute,
    String mySummonerName
) {
    public static AnalysisContext champSelect(LcuChampSelectDTO cs, String myChamp, String myRole, 
                                               String enemyChamp, String enemyRole, String summonerName) {
        return new AnalysisContext(AnalysisTrigger.CHAMP_SELECT_END, cs, null, null, 
                                   myChamp, myRole, enemyChamp, enemyRole, 0, summonerName);
    }

    public static AnalysisContext death(LiveClientAllDataDTO data, LiveClientEventDTO deathEvent,
                                        String myChamp, String summonerName, int minute) {
        return new AnalysisContext(AnalysisTrigger.DEATH, null, data, deathEvent,
                                   myChamp, null, null, null, minute, summonerName);
    }

    public static AnalysisContext gameEnd(LiveClientAllDataDTO data, String myChamp, String summonerName) {
        return new AnalysisContext(AnalysisTrigger.GAME_END, null, data, null,
                                   myChamp, null, null, null, 0, summonerName);
    }
}
