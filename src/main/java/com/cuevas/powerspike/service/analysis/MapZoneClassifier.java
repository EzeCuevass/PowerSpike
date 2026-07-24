package com.cuevas.powerspike.service.analysis;

import com.cuevas.powerspike.dto.LiveClientEventDTO;
import com.cuevas.powerspike.dto.LiveClientEventsDTO;
import com.cuevas.powerspike.dto.LiveClientPlayerDTO;
import com.cuevas.powerspike.dto.LiveClientPositionDTO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Clasifica la zona del mapa donde ocurrió una muerte a partir de coordenadas (x, y).
 * El mapa de LoL es ~15000x15000.
 * ORDER = equipo azul (izquierda), CHAOS = equipo rojo (derecha).
 */
public class MapZoneClassifier {

    public enum Zone {
        TOP_LANE("top lane"),
        MID_LANE("mid lane"),
        BOT_LANE("bot lane"),
        RIVER("el río"),
        OWN_JUNGLE("jungla propia"),
        ENEMY_JUNGLE("jungla enemiga"),
        DRAGON_PIT("la zona del dragón"),
        BARON_PIT("la zona del barón"),
        BLUE_BUFF("el blue buff"),
        RED_BUFF("el red buff"),
        BASE("la base"),
        UNKNOWN("zona desconocida");

        public final String label;
        Zone(String label) { this.label = label; }
    }

    /**
     * Clasifica la zona donde murió el jugador.
     */
    public static Zone classify(double x, double y, String playerTeam) {
        if (x == 0 && y == 0) return Zone.UNKNOWN;

        // Dragón (inferior central, ~7500, 3500-4500)
        if (dist(x, y, 7500, 4000) < 1500) return Zone.DRAGON_PIT;

        // Barón (superior central, ~7500, 10000-11000)
        if (dist(x, y, 7500, 10500) < 1500) return Zone.BARON_PIT;

        // Blue buff azul (~3800, 7800)
        if (dist(x, y, 3800, 7800) < 1200) return Zone.BLUE_BUFF;
        // Red buff azul (~6800, 4200)
        if (dist(x, y, 6800, 4200) < 1200) return Zone.RED_BUFF;
        // Blue buff rojo (~11000, 6800)
        if (dist(x, y, 11000, 6800) < 1200) return Zone.BLUE_BUFF;
        // Red buff rojo (~8200, 10500)
        if (dist(x, y, 8200, 10500) < 1200) return Zone.RED_BUFF;

        boolean isBlue = "ORDER".equals(playerTeam);

        // Río (centro del mapa)
        if (x > 5500 && x < 9500 && y > 5500 && y < 9500) return Zone.RIVER;

        // Líneas
        if (y > 8500) return Zone.TOP_LANE;
        if (y < 4500) return Zone.BOT_LANE;
        if (isNearMidLane(x, y)) return Zone.MID_LANE;

        // Junglas
        if (isBlue) {
            if (x < 7500) return Zone.OWN_JUNGLE;
            if (x > 7500) return Zone.ENEMY_JUNGLE;
        } else {
            if (x > 7500) return Zone.OWN_JUNGLE;
            if (x < 7500) return Zone.ENEMY_JUNGLE;
        }

        return Zone.UNKNOWN;
    }

    /**
     * Busca eventos de wards (WARD_PLACED, WARD_KILL) en los 2 minutos previos a la muerte.
     * Retorna true si había una ward viva cerca de la posición de muerte.
     */
    public static boolean hasNearbyVision(LiveClientEventsDTO events, double deathTime, LiveClientPositionDTO deathPos) {
        if (events == null || events.Events() == null || deathPos == null) return false;

        double cutOff = deathTime - 120; // Últimos 2 minutos
        boolean wardPlaced = false;
        double wardPlacedTime = 0;

        for (LiveClientEventDTO e : events.Events()) {
            if (e.EventTime() < cutOff || e.EventTime() > deathTime) continue;

            if ("WARD_PLACED".equals(e.EventName()) && e.position() != null) {
                if (dist(e.position(), deathPos) < 2500) {
                    wardPlaced = true;
                    wardPlacedTime = e.EventTime();
                }
            }

            // Si la ward fue destruida después de ser puesta, ya no sirve
            if ("WARD_KILL".equals(e.EventName()) && e.position() != null) {
                if (wardPlaced && e.EventTime() > wardPlacedTime
                        && dist(e.position(), deathPos) < 2500) {
                    wardPlaced = false;
                }
            }
        }

        return wardPlaced;
    }

    /**
     * Cuenta cuántos aliados estaban cerca (radio 3000) en el momento de la muerte.
     */
    public static int countNearbyAllies(List<LiveClientPlayerDTO> allPlayers, String myName, double deathX, double deathY) {
        int count = 0;
        for (LiveClientPlayerDTO p : allPlayers) {
            if (p.summonerName().equals(myName)) continue;
            // No tenemos coordenadas de jugadores en el DTO actual, esto es una aproximación
            // Por ahora retornamos 0, se podría mejorar si agregamos coordenadas al player DTO
        }
        return count;
    }

    private static double dist(double x1, double y1, double x2, double y2) {
        return Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
    }

    private static double dist(LiveClientPositionDTO p1, LiveClientPositionDTO p2) {
        return dist(p1.x(), p1.y(), p2.x(), p2.y());
    }

    private static boolean isNearMidLane(double x, double y) {
        double expectedY = x * 0.85 + 500;
        return Math.abs(y - expectedY) < 1500;
    }
}