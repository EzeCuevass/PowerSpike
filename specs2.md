# PowerSpike - Especificaciones (Pre-split)

Aplicación desktop tipo Blitz.gg para League of Legends integrada con IA.
Stack: Java 24, Spring Boot 4.1, JavaFX 21, Gradle, H2, JPA, Apache HttpClient 5, WebSocket, JNA, Jackson 3.x.

---

## Conexión con League of Legends

| Componente | Descripción |
|-----------|-------------|
| **LCU API** | Detección del cliente vía WMIC, polling cada 2s de gameflow-phase, endpoints REST |
| **LCU WebSocket** | Conexión en tiempo real a `wss://127.0.0.1:{port}`, suscripción a `OnJsonApiEvent`, filtro por `/lol-champ-select/v1/session`, reconexión automática (5 intentos) |
| **Live Client API** | Conexión a `localhost:2999` con SSL trust-all (Apache HttpClient 5), polling cada 2s de stats en vivo |
| **Riot API** | Account v1, Summoner v4, Spectator v5, Match v5 |

## UI JavaFX

| Componente | Descripción |
|-----------|-------------|
| **4 tabs** | Perfil, Champ Select, Partida, Coach IA |
| **Dark theme** | CSS completo con variables, estilo GitHub dark |
| **Header** | PowerSpike logo + status indicator (bolita verde/roja/azul/naranja según fase) + sesión activa (foto, nombre, nivel, "Cerrar sesión") |
| **Tab Perfil** | Buscador de invocador + Match History (cards con campeón, KDA, W/L, duración, items) |
| **Tab Champ Select** | Picks, bans, timer en vivo vía WebSocket |
| **Tab Partida** | Stats del jugador (KDA, CS, oro) + equipos de 10 jugadores con campeones |

## Overlay

| Feature | Descripción |
|---------|-------------|
| **Ventana click-through** | 2 stages: control bar (clickeable, X + drag) + overlay (JNA `WS_EX_TRANSPARENT`) |
| **Alto dinámico** | Se expande/colapsa según el largo del consejo (máx 700px) |
| **Fade out** | 15 segundos de visibilidad, luego fade de 300ms |
| **F5 toggle** | Mostrar/ocultar |
| **Auto-show** | Se activa cuando la fase = InProgress |
| **Posición** | Top-right, draggable (coordenadas de pantalla, no del nodo) |

## Motor de análisis con IA (gpt-5.6-luna)

| Trigger | Cuándo | Qué envía |
|---------|--------|-----------|
| **Matchup concreto** | Live Client conecta por primera vez | 10 jugadores con roles exactos, enemigo directo de línea |
| **Muerte** | Jugador muere (detecta ChampionKill con su nombre) | Stats, killer, asistentes, visión, cooldown de 30s, screenshot 1024x1024 base64 |
| **Post-game** | Fase sale de InProgress | 10 jugadores con KDA/CS, score de equipos, duración |
| **Objetivos próximos** | 30s antes de spawn | Dragón (5:00 +5min), Heraldo (14:00 +6min), Barón (20:00 +6min), Larvas (6:00 una vez) |

### Prompt de muerte (el más rico)

```
Moriste en el minuto 11.

Sos Viego (Lv.7, 3/1/2, 68 CS) jugando jungle.
Tus items: Emberknife, Boots

Te mató Lee Sin (Lv.8, 3/0/1, 72 CS).
Sus items: Emberknife, Long Sword

Contexto:
- Zona: desconocida
- Tipo de pelea: Te mataron entre 2 enemigos
- Visión: NO wardaste en los últimos 2 minutos.
- Asistentes del killer: Braum
- Killer Lv.8 vs Lv.7
- Todos tus aliados están vivos.

[Análisis visual con instrucciones detalladas:
MINIMAPA, COOLDOWNS, VISIÓN, ENEMIGOS/ALIADOS, ¿ERA EVITABLE?]
```

## Match History

| Feature | Descripción |
|---------|-------------|
| **Match v5 API** | Últimas 20 partidas por puuid |
| **Caché H2 en disco** | Persiste entre sesiones (`data/powerspike.mv.db`) |
| **Items con nombres** | Data Dragon item.json cargado al inicio |
| **Tiempo relativo** | "hace 1h", "hace 2d" |
| **Tab Perfil** | Cards con campeón, KDA verde/rojo, W/L, duración, items |

## Sesión persistente

| Feature | Descripción |
|---------|-------------|
| **Guardar al buscar** | Nombre, tag, puuid, icono, nivel → H2 |
| **Restaurar al abrir** | `@PostConstruct` en GameStateService |
| **Botón cerrar sesión** | Limpia H2 + UI instantáneamente |
| **Cambio de sesión** | Limpia datos viejos antes de cargar nuevos |

## Infraestructura

| Componente | Descripción |
|-----------|-------------|
| **Java 24 + Gradle 9.5** | Records Java, pattern matching |
| **Spring Boot 4.1** | REST controllers, JPA, scheduling |
| **Jackson 3.x** | `tools.jackson` (sin anotaciones, con `FAIL_ON_NULL_FOR_PRIMITIVES=false`) |
| **SLF4J** | Cero `System.out.println`, todo con logger |
| **H2 en disco** | `ddl-auto=update`, tablas: `summoner_entity`, `match_entity`, `active_session` |
| **JNA 5.15** | Click-through en overlay (`WS_EX_TRANSPARENT`) |
| **Data Dragon dinámico** | Versión de `versions.json`, campeones + items |

## Testing

| Tipo | Estado |
|------|--------|
| Compilación | ✅ BUILD SUCCESSFUL |
| IntelliJ MCP | ✅ 0 errores en todos los archivos |
| Ejecución real | ✅ Champ select, muertes, post-game, overlay |
