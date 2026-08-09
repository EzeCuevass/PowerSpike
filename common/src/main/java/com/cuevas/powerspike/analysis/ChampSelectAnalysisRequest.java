package com.cuevas.powerspike.analysis;

import com.cuevas.powerspike.dto.LcuChampSelectDTO;
import com.cuevas.powerspike.dto.LiveClientAllDataDTO;

/**
 * Request de análisis de champ select.
 * champSelect viene del LCU (roles propios confirmados).
 * liveClientData es un fallback cuando la app arrancó tarde y no hay datos de LCU
 * (en ese caso champSelect es null y se usa liveClientData en su lugar).
 */
public record ChampSelectAnalysisRequest(
        LcuChampSelectDTO champSelect,
        LiveClientAllDataDTO liveClientData,
        String mySummonerName,
        String myChampion,
        String myRole,
        String enemyChampion,
        String enemyRole
) {}
