package com.cuevas.powerspike.service.analysis;

import com.cuevas.powerspike.dto.*;
import com.cuevas.powerspike.service.DataDragonClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Constructor de prompts para la IA.
 *
 * Arma prompts específicos según el tipo de análisis:
 *   - buildChampSelectPrompt: análisis de matchup al terminar la selección
 *   - buildDeathPrompt: consejo rápido cuando el jugador muere
 *   - buildGameEndPrompt: análisis post-game completo
 *
 * Los prompts incluyen contexto del juego (campeones, stats, items, eventos)
 * para que la IA pueda dar recomendaciones específicas y accionables.
 */
@Component
public class PromptBuilder {

    private final DataDragonClient dataDragonClient;

    public PromptBuilder(DataDragonClient dataDragonClient) {
        this.dataDragonClient = dataDragonClient;
    }

    /**
     * Arma el prompt para análisis de champ select.
     * Incluye: jugador, campeón, rol, enemigo de línea, composición de ambos equipos y baneos.
     * Pide: cómo jugar la línea, build, runas, tips específicos y debilidades enemigas.
     */
    public String buildChampSelectPrompt(AnalysisContext ctx) {

        LcuChampSelectDTO cs = ctx.champSelect();
        String myTeamStr = formatTeam(cs.myTeam());
        String enemyTeamStr = formatTeam(cs.theirTeam());
        String bansStr = formatBans(cs.bans());

        String formatted = """
                Terminó el champ select. Los roles del equipo enemigo no están confirmados, así que vas a tener que deducirlos.
                
                Sos %s jugando %s (%s).
                
                Tu equipo (roles confirmados):
                %s
                
                Equipo enemigo (campeones visibles, roles sin confirmar):
                %s
                
                Baneos: %s
                Patch: %s
                
                Primero, analizá los campeones enemigos y asignales el rol MÁS PROBABLE según el meta actual (patch %s).
                
                Después, con esa composición estimada, decime:
                1. Quién es tu enemigo de línea más probable y cómo jugarle
                2. Cómo jugar el early/mid/late según el matchup
                3. Build recomendada para esta partida en general
                4. Debilidades de la composición enemiga
                5. Qué va a querer hacer el equipo enemigo (teamfight, split push, pickoffs)
                
                Sé conciso. Es un análisis especulativo, no tenés los roles confirmados.
                Usá saltos de línea para separar secciones y que la respuesta sea legible. No escribas todo en un solo párrafo.
                """.formatted(
                ctx.mySummonerName(),
                ctx.myChampion(),
                ctx.myRole() != null && !ctx.myRole().isEmpty() ? ctx.myRole() : "rol desconocido",
                myTeamStr,
                enemyTeamStr,
                bansStr,
                dataDragonClient.getCurrentVersion(),
                dataDragonClient.getCurrentVersion()
        );

        return formatted;
    }

    /**
     * Análisis desde LiveClient cuando la app no tiene datos de LCU (inicio tardío).
     */
    public String buildChampSelectFromLiveClient(LiveClientAllDataDTO data, LiveClientPlayerDTO myPlayer) {
        String myTeam = data.allPlayers().stream()
                .filter(p -> p.team().equals(myPlayer.team()))
                .map(p -> "%s (%s) [%s]".formatted(p.championName(), p.riotId(), p.position()))
                .collect(java.util.stream.Collectors.joining(", "));

        String enemyTeam = data.allPlayers().stream()
                .filter(p -> !p.team().equals(myPlayer.team()))
                .map(p -> "%s (%s) [%s]".formatted(p.championName(), p.riotId(), p.position()))
                .collect(java.util.stream.Collectors.joining(", "));

        return """
                Estás en partida. Estos son los datos reales de la composición.
                
                Sos %s jugando %s (%s).
                
                Tu equipo: %s
                Equipo enemigo: %s
                
                Analizá:
                1. Quién es tu enemigo directo y cómo jugarle
                2. Fortalezas y debilidades de la composición enemiga
                3. Estrategia recomendada para esta partida
                
                Sé conciso.
                Usá saltos de línea para separar secciones y que la respuesta sea legible. No escribas todo en un solo párrafo.
                """.formatted(
                myPlayer.riotId(), myPlayer.championName(), myPlayer.position(),
                myTeam, enemyTeam
        );
    }

    /**
     * Arma el prompt para análisis concreto de matchup cuando el Live Client conecta.
     * A diferencia del champ select (especulativo), acá tenemos roles exactos de los 10 jugadores.
     */
    public String buildLiveClientMatchupPrompt(LiveClientAllDataDTO data, 
                                                LiveClientPlayerDTO myPlayer,
                                                LiveClientPlayerDTO enemyPlayer) {
        String myTeamComp = data.allPlayers().stream()
                .filter(p -> p.team().equals(myPlayer.team()))
                .map(p -> "%s (%s) [%s]".formatted(p.championName(), p.riotId(), p.position()))
                .collect(java.util.stream.Collectors.joining(", "));

        String enemyTeamComp = data.allPlayers().stream()
                .filter(p -> !p.team().equals(myPlayer.team()))
                .map(p -> "%s (%s) [%s]".formatted(p.championName(), p.riotId(), p.position()))
                .collect(java.util.stream.Collectors.joining(", "));

        if (enemyPlayer == null) {
            return """
                    Ya arrancó la partida.
                    
                    Sos %s jugando %s (%s).
                    
                    Tu equipo: %s
                    Equipo enemigo: %s
                    
                    No se pudo determinar el enemigo directo de tu línea.
                    Analizá la composición enemiga y damé consejos generales de cómo jugar esta partida.
                    """.formatted(
                    myPlayer.riotId(), myPlayer.championName(), myPlayer.position(),
                    myTeamComp, enemyTeamComp
            );
        }

        return """
                Ya arrancó la partida. Estos son los datos reales del matchup.
                
                Sos %s jugando %s (%s).
                Tu enemigo directo es %s (%s) jugando %s.
                
                Tu equipo: %s
                Equipo enemigo: %s
                
                Con esta información concreta, analizá:
                1. Cómo jugar contra %s en early (lvl 1-6), mid (lvl 6-11) y late (lvl 11+)
                2. Items recomendados específicos contra este matchup (armadura? MR? heal cut?)
                3. Cuándo tenés ventaja para tradear/pelear (power spikes, niveles clave)
                4. Cuándo NO pelear (él tiene ventaja en early/mid/late)
                5. Estrategia de línea: ¿pusheás, freezás, roameás?
                6. Tips de posicionamiento y mecánica contra este campeón
                
                Sé específico, no genérico. Conocés el matchup exacto.
                Usá saltos de línea para separar secciones y que la respuesta sea legible. No escribas todo en un solo párrafo.
                """.formatted(
                myPlayer.riotId(), myPlayer.championName(), myPlayer.position(),
                enemyPlayer.riotId(), enemyPlayer.championName(), enemyPlayer.position(),
                myTeamComp, enemyTeamComp,
                enemyPlayer.championName()
        );
    }

    /**
     * Arma el prompt para análisis de muerte con contexto rico.
     * Incluye: zona del mapa, visión, tipo de pelea (1v1 vs gank), comparación con el killer.
     */
    public String buildDeathPrompt(LiveClientAllDataDTO data, LiveClientEventDTO death,
                                    String myRole, String deathZone, String visionText,
                                    String fightType, String killerComparison, String assistersList,
                                    String myTeam, String deadAlliesText) {
        LiveClientActivePlayerDTO ap = data.activePlayer();
        LiveClientPlayerDTO myPlayer = findMyPlayer(data);
        LiveClientPlayerDTO killer = findKiller(data, death);

        String myStats = formatPlayerStats(myPlayer);
        String killerStats = killer != null ? formatPlayerStats(killer) : "desconocido";
        String myItems = formatItems(myPlayer);
        String killerItems = killer != null ? formatItems(killer) : "desconocido";
        String killerName = death != null && death.KillerName() != null ? death.KillerName() : "desconocido";
        int minute = (int)(death.EventTime() / 60.0);

        return """
                Moriste en el minuto %d.
                
                Sos %s (%s) jugando %s.
                Tus stats: %s
                Tus items: %s
                
                Te mató %s (%s).
                Sus stats: %s
                Sus items: %s
                
                Contexto:
                - Zona: %s
                - Tipo de pelea: %s
                - Visión: %s
                %s
                %s
                - %s
                
                Analizá la captura de LoL como un coach en tiempo real.
                Usá internamente este análisis para darme UN consejo accionable.
                No imprimas el análisis, solo la conclusión.
                
                Tu análisis interno (no lo imprimas):
                - Lado: tu equipo es %s (%s, jungla al %s lado del mapa)
                - Ubicación: zona exacta (top, bot, río, jungla, blue buff, etc.)
                - Visión: wards aliadas o enemigas visibles
                - ¿Aliados o enemigos cerca?
                - Cooldowns disponibles o gastados
                - ¿Era evitable? ¿Por qué?
                
                Al final, damé solo UN consejo concreto y breve basado en TODO (texto + captura). Máximo 3 líneas.
                """.formatted(
                minute,
                ap.championName(),
                myStats,
                myRole != null ? myRole : "rol desconocido",
                myStats,
                myItems,
                killerName,
                killer != null ? killer.championName() : "desconocido",
                killerStats,
                killerItems,
                deathZone,
                fightType,
                visionText,
                killerComparison,
                assistersList != null && !assistersList.isEmpty() ? assistersList : "",
                deadAlliesText,
                "ORDER".equals(myTeam) ? "Azul (ORDER), jungla al lado IZQUIERDO del mapa" : "Rojo (CHAOS), jungla al lado DERECHO del mapa",
                "ORDER".equals(myTeam) ? "Azul" : "Rojo",
                "ORDER".equals(myTeam) ? "izquierdo" : "derecho"
        );
    }

    /**
     * Arma el prompt para aviso de objetivo próximo a spawnear con contexto de equipo.
     */
    public String buildObjectiveSpawnPrompt(LiveClientAllDataDTO data, String objective, double spawnTime,
                                            int myKills, int enemyKills, int advantage, long deadAllies) {
        int minute = (int)(spawnTime / 60);
        int seconds = (int)(spawnTime % 60);

        String myChamp = data.activePlayer() != null ? data.activePlayer().championName() : "desconocido";
        LiveClientPlayerDTO myPlayer = findMyPlayer(data);
        String myRole = myPlayer != null && myPlayer.position() != null ? myPlayer.position() : "rol desconocido";
        String myStats = myPlayer != null ? formatPlayerStats(myPlayer) : "N/A";
        String myItems = formatItems(myPlayer);

        String advantageStr = advantage > 2 ? "Tu equipo está fuerte (+" + advantage + " kills). Pelealo."
                : advantage < -2 ? "Tu equipo va perdiendo (" + Math.abs(advantage) + " kills abajo). Pensalo bien."
                : "Está parejo (" + advantage + " kills de diferencia). Depende del posicionamiento.";

        String deadStr = deadAllies > 0 ? "Tenés " + deadAllies + " aliado(s) muerto(s) ahora. No conviene pelear sin ellos."
                : "Todos los aliados están vivos.";

        return """
                %s va a aparecer en ~30 segundos (%d:%02d).
                
                Sos %s jugando %s (%s).
                Tus stats: %s
                Tus items: %s
                
                Score: tu equipo %d kills - enemigos %d kills
                %s
                %s
                
                Dame un consejo breve y accionable: ¿vale la pena pelearlo o no? ¿Qué hacer ahora?
                Máximo 3 líneas.
                """.formatted(
                objective, minute, seconds,
                myChamp, myStats, myRole,
                myStats, myItems,
                myKills, enemyKills,
                advantageStr, deadStr
        );
    }

    /**
     * Arma el prompt para análisis post-game.
     * Incluye: duración, stats finales, build, score del equipo, lista de todos los jugadores.
     * Pide: resumen, qué hizo bien, 3 áreas de mejora y qué practicar.
     */
    public String buildGameEndPrompt(AnalysisContext ctx) {
        LiveClientAllDataDTO data = ctx.liveGameData();
        if (data == null) return "No hay datos de la partida disponibles.";

        LiveClientActivePlayerDTO ap = data.activePlayer();
        LiveClientPlayerDTO myPlayer = findMyPlayer(data);

        String allPlayersStr = data.allPlayers().stream()
                .map(p -> "%s (%s) [%s] - %d/%d/%d, %d CS"
                        .formatted(
                                p.riotId() != null && !p.riotId().isEmpty() ? p.riotId() : p.summonerName(),
                                p.championName(),
                                p.team(),
                                p.scores() != null ? p.scores().kills() : 0,
                                p.scores() != null ? p.scores().deaths() : 0,
                                p.scores() != null ? p.scores().assists() : 0,
                                p.scores() != null ? p.scores().creepScore() : 0
                        ))
                .collect(Collectors.joining("\n"));

        String myStats = myPlayer != null ? formatPlayerStats(myPlayer) : "N/A";
        String myItems = formatItems(myPlayer);

        // Calcula la duración de la partida según el último evento
        double gameMinutes = data.events() != null && data.events().Events() != null
                ? data.events().Events().stream()
                    .mapToDouble(LiveClientEventDTO::EventTime)
                    .max().orElse(0) / 60.0
                : 0;

        // Suma kills de cada equipo (ORDER = azul, CHAOS = rojo)
        int totalKillsBlue = data.allPlayers().stream()
                .filter(p -> "ORDER".equals(p.team()))
                .mapToInt(p -> p.scores() != null ? p.scores().kills() : 0).sum();
        int totalKillsRed = data.allPlayers().stream()
                .filter(p -> "CHAOS".equals(p.team()))
                .mapToInt(p -> p.scores() != null ? p.scores().kills() : 0).sum();

        return """
                La partida terminó (%.1f minutos).
                
                Sos %s (%s).
                Tus stats finales: %s
                Build: %s
                
                Score: Azul %d - Rojo %d
                
                Todos los jugadores:
                %s
                
                Analizá mi rendimiento y damé:
                1. Resumen breve de cómo jugué
                2. Qué hice bien
                3. Que errores tuve
                4. 3 áreas concretas de mejora
                5. Qué debería practicar para la próxima
                
                Sé honesto y constructivo.
                Usá saltos de línea para separar secciones y que la respuesta sea legible. No escribas todo en un solo párrafo.
                """.formatted(
                gameMinutes,
                ctx.mySummonerName(),
                ap.championName(),
                myStats,
                myItems,
                totalKillsBlue,
                totalKillsRed,
                allPlayersStr
        );
    }

    /**
     * Formatea la composición de un equipo para el prompt.
     * Incluye campeones incluso si los datos del jugador no están disponibles (puuid vacío).
     */
    private String formatTeam(List<LcuTeamMemberDTO> team) {
        if (team == null || team.isEmpty()) return "N/A";
        
        // Separar jugadores con nombre vs sin nombre (enemigos sin datos)
        List<LcuTeamMemberDTO> namedPlayers = team.stream()
                .filter(m -> m.gameName() != null && !m.gameName().isEmpty() && !m.gameName().equals("#"))
                .collect(Collectors.toList());
        
        List<LcuTeamMemberDTO> champOnlyPlayers = team.stream()
                .filter(m -> m.gameName() == null || m.gameName().isEmpty() || m.gameName().equals("#"))
                .filter(m -> m.championId() > 0 || m.championPickIntent() > 0)
                .collect(Collectors.toList());
        
        StringBuilder sb = new StringBuilder();
        
        // Jugadores con nombre
        for (LcuTeamMemberDTO m : namedPlayers) {
            if (!sb.isEmpty()) sb.append(", ");
            String name = m.gameName() + "#" + m.tagLine();
            String champ = m.championId() > 0 ? dataDragonClient.getChampionName(m.championId()) : "Sin pick";
            String pos = m.assignedPosition() != null && !m.assignedPosition().isEmpty() ? "[" + m.assignedPosition() + "]" : "";
            sb.append("%s (%s) %s".formatted(name, champ != null ? champ : "?", pos));
        }
        
        // Campeones sin nombre (enemigos)
        if (!champOnlyPlayers.isEmpty()) {
            if (!sb.isEmpty()) sb.append(" | Enemigos: ");
            for (LcuTeamMemberDTO m : champOnlyPlayers) {
                int champId = m.championId() > 0 ? m.championId() : m.championPickIntent();
                String champ = dataDragonClient.getChampionName(champId);
                sb.append(champ != null ? champ : "Champion " + champId).append(", ");
            }
            // Sacar la última coma
            sb.setLength(sb.length() - 2);
        }
        
        return !sb.isEmpty() ? sb.toString() : "N/A";
    }

    /**
     * Formatea los baneos de ambos equipos.
     * Formato: "Azul: champ1, champ2 | Rojo: champ3, champ4"
     */
    private String formatBans(LcuBansDTO bans) {
        if (bans == null) return "N/A";
        StringBuilder sb = new StringBuilder();
        if (bans.myTeamBans() != null && !bans.myTeamBans().isEmpty()) {
            sb.append("Azul: ");
            sb.append(bans.myTeamBans().stream()
                    .map(b -> dataDragonClient.getChampionName(b.championId()))
                    .filter(n -> n != null)
                    .collect(Collectors.joining(", ")));
        }
        if (bans.theirTeamBans() != null && !bans.theirTeamBans().isEmpty()) {
            if (!sb.isEmpty()) sb.append(" | ");
            sb.append("Rojo: ");
            sb.append(bans.theirTeamBans().stream()
                    .map(b -> dataDragonClient.getChampionName(b.championId()))
                    .filter(n -> n != null)
                    .collect(Collectors.joining(", ")));
        }
        return sb.isEmpty() ? "Sin baneos" : sb.toString();
    }

    /**
     * Formatea las stats de un jugador: "Lv.X, K/D/A, CS"
     */
    private String formatPlayerStats(LiveClientPlayerDTO p) {
        if (p == null) return "N/A";
        return "Lv.%d, %d/%d/%d, %d CS".formatted(
                p.level(),
                p.scores() != null ? p.scores().kills() : 0,
                p.scores() != null ? p.scores().deaths() : 0,
                p.scores() != null ? p.scores().assists() : 0,
                p.scores() != null ? p.scores().creepScore() : 0
        );
    }

    /**
     * Formatea los items de un jugador separados por coma.
     * Filtra items con ID > 0 (slots vacíos tienen ID 0).
     */
    private String formatItems(LiveClientPlayerDTO p) {
        if (p == null || p.items() == null || p.items().isEmpty()) return "Sin items";
        return p.items().stream()
                .filter(i -> i.itemID() > 0)
                .map(LiveClientItemDTO::displayName)
                .collect(Collectors.joining(", "));
    }

    /**
     * Busca al jugador en la lista de todos los jugadores por summonerName o riotId.
     */
    private LiveClientPlayerDTO findMyPlayer(LiveClientAllDataDTO data) {
        if (data == null || data.activePlayer() == null || data.allPlayers() == null) return null;
        String myName = data.activePlayer().summonerName();
        return data.allPlayers().stream()
                .filter(p -> p.summonerName().equals(myName) || 
                             (p.riotId() != null && p.riotId().contains(myName)))
                .findFirst().orElse(null);
    }

    /**
     * Busca al killer en la lista de jugadores por nombre, riotId o nombre de campeón.
     * El último fallback es por campeón porque a veces el KillerName es el nombre del champ.
     */
    private LiveClientPlayerDTO findKiller(LiveClientAllDataDTO data, LiveClientEventDTO death) {
        if (data == null || death == null || death.KillerName() == null || data.allPlayers() == null) return null;
        String killerName = death.KillerName();
        return data.allPlayers().stream()
                .filter(p -> p.summonerName().equals(killerName) || 
                             (p.riotId() != null && p.riotId().contains(killerName)) ||
                             p.championName().equals(killerName))
                .findFirst().orElse(null);
    }
}
