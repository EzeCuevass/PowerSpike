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
        System.out.println(">>> [DEBUG PB-CS] === INICIO buildChampSelectPrompt ===");
        System.out.println(">>> [DEBUG PB-CS] myChampion: " + ctx.myChampion());
        System.out.println(">>> [DEBUG PB-CS] myRole: " + ctx.myRole());
        System.out.println(">>> [DEBUG PB-CS] enemyChampion: " + ctx.enemyChampion());
        System.out.println(">>> [DEBUG PB-CS] enemyRole: " + ctx.enemyRole());
        System.out.println(">>> [DEBUG PB-CS] mySummonerName: " + ctx.mySummonerName());
        System.out.println(">>> [DEBUG PB-CS] champSelect.myTeam.size: " + (ctx.champSelect() != null && ctx.champSelect().myTeam() != null ? ctx.champSelect().myTeam().size() : "null"));
        System.out.println(">>> [DEBUG PB-CS] champSelect.theirTeam.size: " + (ctx.champSelect() != null && ctx.champSelect().theirTeam() != null ? ctx.champSelect().theirTeam().size() : "null"));
        System.out.println(">>> [DEBUG PB-CS] champSelect.bans: " + ctx.champSelect().bans());

        LcuChampSelectDTO cs = ctx.champSelect();
        String myTeamStr = formatTeam(cs.myTeam());
        String enemyTeamStr = formatTeam(cs.theirTeam());
        String bansStr = formatBans(cs.bans());

        System.out.println(">>> [DEBUG PB-CS] myTeamStr: " + myTeamStr);
        System.out.println(">>> [DEBUG PB-CS] enemyTeamStr: " + enemyTeamStr);
        System.out.println(">>> [DEBUG PB-CS] bansStr: " + bansStr);

        String formatted = """
                Terminó el champ select.
                
                Sos %s jugando %s.
                Tu matchup en la línea es contra %s (%s).
                
                Tu equipo: %s
                Equipo enemigo: %s
                Baneos: %s
                
                Analizá el matchup y damé:
                1. Cómo jugar la línea (early/mid/late)
                2. Build recomendada y por qué
                3. Runas sugeridas
                4. Tips específicos contra tu enemigo de línea
                5. Debilidades de la composición enemiga
                
                Sé conciso y práctico.
                """.formatted(
                ctx.mySummonerName(),
                ctx.myChampion(),
                ctx.enemyChampion() != null ? ctx.enemyChampion() : "desconocido",
                ctx.enemyRole() != null ? ctx.enemyRole() : "rol desconocido",
                myTeamStr,
                enemyTeamStr,
                bansStr
        );

        System.out.println(">>> [DEBUG PB-CS] Prompt final:\n" + formatted);

        return formatted;
    }

    /**
     * Arma el prompt para análisis de muerte.
     * Incluye: minuto, campeón del jugador, stats, items, datos del killer,
     * rol del jugador, fase de la partida, y eventos cercanos.
     * Pide: UN consejo breve y accionable (máximo 3 líneas).
     */
    public String buildDeathPrompt(AnalysisContext ctx, String myRole, String gamePhase, String nearbyEvents) {
        System.out.println(">>> [DEBUG PB-DEATH] gameMinute: " + ctx.gameMinute());
        System.out.println(">>> [DEBUG PB-DEATH] myChampion: " + ctx.myChampion());
        System.out.println(">>> [DEBUG PB-DEATH] myRole: " + myRole);
        System.out.println(">>> [DEBUG PB-DEATH] gamePhase: " + gamePhase);
        System.out.println(">>> [DEBUG PB-DEATH] nearbyEvents: " + nearbyEvents);
        System.out.println(">>> [DEBUG PB-DEATH] mySummonerName: " + ctx.mySummonerName());
        System.out.println(">>> [DEBUG PB-DEATH] deathEvent.EventName: " + (ctx.deathEvent() != null ? ctx.deathEvent().EventName() : "null"));
        System.out.println(">>> [DEBUG PB-DEATH] deathEvent.KillerName: " + (ctx.deathEvent() != null ? ctx.deathEvent().KillerName() : "null"));
        System.out.println(">>> [DEBUG PB-DEATH] liveGameData.activePlayer: " + (ctx.liveGameData() != null && ctx.liveGameData().activePlayer() != null ? ctx.liveGameData().activePlayer().summonerName() : "null"));

        LiveClientAllDataDTO data = ctx.liveGameData();
        LiveClientActivePlayerDTO ap = data.activePlayer();
        LiveClientEventDTO death = ctx.deathEvent();

        LiveClientPlayerDTO myPlayer = findMyPlayer(data);
        LiveClientPlayerDTO killer = findKiller(data, death);

        System.out.println(">>> [DEBUG PB-DEATH] myPlayer: " + (myPlayer != null ? myPlayer.summonerName() : "null"));
        System.out.println(">>> [DEBUG PB-DEATH] killer: " + (killer != null ? killer.summonerName() : "null"));

        String myStats = formatPlayerStats(myPlayer);
        String killerStats = killer != null ? formatPlayerStats(killer) : "desconocido";
        String myItems = formatItems(myPlayer);
        String killerItems = killer != null ? formatItems(killer) : "desconocido";

        String killerName = death != null && death.KillerName() != null ? death.KillerName() : "desconocido";

        // Determinar posición aproximada según rol
        String positionHint = switch (myRole != null ? myRole.toLowerCase() : "") {
            case "top" -> "top lane";
            case "jungle" -> "jungla";
            case "middle" -> "mid lane";
            case "bottom" -> "bot lane";
            case "utility" -> "bot lane (support)";
            default -> "desconocida";
        };

        return """
                Moriste en el minuto %d (%s).
                
                Sos %s (%s) jugando %s.
                Estabas en %s.
                Tus stats: %s
                Tus items: %s
                
                Te mató %s (%s).
                Sus stats: %s
                Sus items: %s
                
                Eventos cercanos (±30s): %s
                
                Damé UN consejo breve y accionable sobre qué hacer diferente según tu rol y la fase de la partida. Máximo 3 líneas.
                """.formatted(
                ctx.gameMinute(),
                gamePhase,
                ap.championName(),
                myStats,
                myRole != null ? myRole : "rol desconocido",
                positionHint,
                myStats,
                myItems,
                killerName,
                killer != null ? killer.championName() : "desconocido",
                killerStats,
                killerItems,
                nearbyEvents
        );
    }

    /**
     * Arma el prompt para análisis post-game.
     * Incluye: duración, stats finales, build, score del equipo, lista de todos los jugadores.
     * Pide: resumen, qué hizo bien, 3 áreas de mejora y qué practicar.
     */
    public String buildGameEndPrompt(AnalysisContext ctx) {
        System.out.println(">>> [DEBUG PB-END] myChampion: " + ctx.myChampion());
        System.out.println(">>> [DEBUG PB-END] mySummonerName: " + ctx.mySummonerName());
        System.out.println(">>> [DEBUG PB-END] liveGameData: " + (ctx.liveGameData() != null ? "presente" : "null"));

        LiveClientAllDataDTO data = ctx.liveGameData();
        if (data == null) return "No hay datos de la partida disponibles.";

        LiveClientActivePlayerDTO ap = data.activePlayer();
        LiveClientPlayerDTO myPlayer = findMyPlayer(data);

        System.out.println(">>> [DEBUG PB-END] activePlayer: " + (ap != null ? ap.summonerName() : "null"));
        System.out.println(">>> [DEBUG PB-END] myPlayer: " + (myPlayer != null ? myPlayer.summonerName() : "null"));
        System.out.println(">>> [DEBUG PB-END] allPlayers.size: " + data.allPlayers().size());

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

        System.out.println(">>> [DEBUG PB-END] gameMinutes: " + gameMinutes);
        System.out.println(">>> [DEBUG PB-END] totalKillsBlue: " + totalKillsBlue);
        System.out.println(">>> [DEBUG PB-END] totalKillsRed: " + totalKillsRed);
        System.out.println(">>> [DEBUG PB-END] allPlayersStr:\n" + allPlayersStr);

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
     * Formato: "Nombre#Tag (Campeón) [ROL]"
     * Filtra bots (puuid vacío) y muestra solo el campeón si no hay nombre.
     */
    private String formatTeam(List<LcuTeamMemberDTO> team) {
        if (team == null || team.isEmpty()) return "N/A";
        
        // Filtrar solo entradas completamente vacías (puuid vacío)
        List<LcuTeamMemberDTO> realPlayers = team.stream()
                .filter(m -> m.puuid() != null && !m.puuid().isEmpty())
                .collect(Collectors.toList());
        
        if (realPlayers.isEmpty()) {
            return "Equipo de bots (practice tool)";
        }
        
        return realPlayers.stream()
                .map(m -> {
                    String name = (m.gameName() != null && !m.gameName().isEmpty() && !m.gameName().equals("#"))
                            ? m.gameName() + "#" + m.tagLine() : null;
                    String champ = m.championId() > 0
                            ? dataDragonClient.getChampionName(m.championId()) 
                            : (m.championPickIntent() > 0 
                                ? dataDragonClient.getChampionName(m.championPickIntent()) + " (hover)"
                                : "Sin pick");
                    String pos = m.assignedPosition() != null && !m.assignedPosition().isEmpty() ? "[" + m.assignedPosition() + "]" : "";
                    if (name != null) {
                        return "%s (%s) %s".formatted(name, champ != null ? champ : "?", pos);
                    }
                    return "%s %s".formatted(champ != null ? champ : "?", pos);
                })
                .collect(Collectors.joining(", "));
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
