# PowerSpike — Especificaciones del Proyecto

## 1. ¿Qué es PowerSpike?

**PowerSpike** es una aplicación de escritorio para Windows que funciona como **coach/asistente de IA en tiempo real para League of Legends**. Se conecta al League Client (LCU) y al Live Client Data API del juego mientras se juega una partida, analiza el estado del juego (composición de equipos, muertes, objetivos, fin de partida) usando un LLM (OpenAI), y le da consejos al jugador en español rioplatense, tanto en la ventana principal de la app como en un **overlay transparente y click-through** que se superpone sobre el propio juego. Opcionalmente puede leer los consejos en voz alta (TTS).

Es una app híbrida: **backend de Spring Boot** (API REST local, persistencia, lógica de negocio) embebido dentro de una **aplicación de escritorio JavaFX**.

---

## 2. Stack Tecnológico

| Categoría | Tecnología |
|---|---|
| Lenguaje | Java 24 (toolchain), Gradle |
| Backend / framework | Spring Boot 4.1.0 (`spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`) |
| UI de escritorio | JavaFX 21 (`javafx-controls`, `javafx-fxml`, `javafx-media`) |
| Base de datos | H2 (embebida, basada en archivo: `data/powerspike.trace.db`) |
| ORM | Spring Data JPA / Hibernate (`ddl-auto=update`, sin migraciones versionadas) |
| Cliente HTTP | `RestTemplate` (Apache HttpClient5) |
| WebSocket | `Java-WebSocket` 1.5.6 (conexión al LCU) |
| Interop nativa | JNA / JNA-Platform (llamadas a Win32 API para click-through del overlay) |
| IA generativa | OpenAI API — Chat Completions (modelo `gpt-5.6-luna`, multimodal con imágenes) y TTS (`gpt-4o-mini-tts`) |
| Utilidades | Lombok |
| Build | Gradle Wrapper (`gradlew` / `gradlew.bat`), plugin `application` y `org.openjfx.javafxplugin` |
| Testing | JUnit 5 (`spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`) |

**Mainclass de ejecución**: `com.cuevas.powerspike.ui.JavaFxApplication` (arranca primero el contexto de Spring Boot vía `SpringApplication.run(...)` y luego lanza la ventana JavaFX, inyectando los beans de Spring en los controllers FXML).

**Referencia de producto**: la app está pensada como una alternativa tipo **Blitz.gg de escritorio**, pero con coaching de IA en tiempo real integrado (no solo estadísticas post-partida).

---

## 3. Arquitectura General

```
┌─────────────────────────────────────────────────────────────┐
│                     JavaFX Application                       │
│  ┌──────────────┐   ┌───────────────────┐                    │
│  │ MainController│   │ Overlay (stage    │                    │
│  │ (main-view)   │   │ transparente,     │                    │
│  │ 4 tabs        │   │ click-through)    │                    │
│  └──────┬───────┘   └─────────┬─────────┘                    │
│         └──────────┬──────────┘                              │
│                     ▼                                        │
│         Spring ApplicationContext (embebido)                 │
│  ┌───────────────────────────────────────────────────────┐   │
│  │ GameStateService  ← estado central (Properties FX)     │  │
│  └──────┬───────────────────┬────────────────┬────────────┘  │
│         ▼                   ▼                ▼               │
│  LcuApi/WebSocket   LiveGamePolling    AnalysisEngine         │
│  (cliente LoL)      (puerto 2999)      (dispara IA)           │
│         │                   │                │               │
│         ▼                   ▼                ▼               │
│  Riot Games API      Live Client API   OpenAI (chat + TTS)   │
│  Data Dragon CDN                        Screenshot (Robot)    │
│                                                                │
│  H2 (data/powerspike.trace.db) ← ActiveSession/Summoner/Match │
└─────────────────────────────────────────────────────────────┘
   API REST local (puerto 8080) expone todo lo anterior también
   vía HTTP (/api/...) para debug/consumo externo.
```

---

## 4. Flujo de la Aplicación (end-to-end)

1. **Arranque**: `JavaFxApplication` levanta el contexto Spring, carga `main-view.fxml`, muestra la ventana principal y crea (ocultos) el overlay transparente y su barra de control.
2. **Detección del cliente de LoL**: `LcuLockfile` detecta el proceso `LeagueClientUx.exe` (vía `wmic`, Windows-only) y extrae puerto + token de autenticación del LCU.
3. **Polling de fase de juego**: `LcuApi` consulta cada 2s `gameflow-phase` (`None`, `Lobby`, `ChampSelect`, `InProgress`, `EndOfGame`).
4. **Champ Select**: al entrar en esa fase, se abre `LcuWebSocket` (WSS al LCU) que escucha eventos en tiempo real de picks/bans/timer y actualiza `GameStateService`. Al finalizar, `AnalysisEngine` dispara un análisis especulativo de matchup (roles enemigos no confirmados).
5. **Partida en curso**: `LiveGamePollingService` hace polling cada 2s del puerto **2999** (Live Client Data API, expuesto por el propio juego). Al detectar la primera conexión, `AnalysisEngine` dispara un análisis de matchup con roles reales confirmados.
6. **Eventos durante la partida** (reactivos a cambios en `GameStateService`):
   - **Muerte del jugador**: se detecta comparando `VictimName` contra el summoner propio. Dispara análisis con **captura de pantalla** (multimodal) y cooldown de 30s.
   - **Objetivo por spawnear** (Dragón, Heraldo, Barón, Larvas): aviso 30s antes del spawn, con contexto de ventaja de kills.
7. **Fin de partida**: al salir de `InProgress`, se dispara un análisis de resumen (aciertos, errores, áreas de mejora).
8. **Presentación del feedback**: cada resultado se muestra en la tab "Coach" de la ventana principal y simultáneamente en el **overlay transparente click-through** (auto-show/hide según si hay partida en curso, toggle manual con **F5**). Opcionalmente se lee en voz alta (TTS, deshabilitado por defecto).
9. **Persistencia**: sesión activa del invocador, caché de datos de invocador (TTL 5 min) y caché permanente de partidas se guardan en H2 para minimizar llamadas a la Riot API (rate-limited).

- **Búsqueda de invocador** por Riot ID (`gameName#tagLine`) y visualización de historial de partidas con winrates por campeón y "peores enemigos" (campeones con peor winrate, mín. 2 partidas).
- **Seguimiento en vivo de Champ Select**: equipos, baneos, timer, actualizado en tiempo real vía WebSocket del LCU.
- **Seguimiento en vivo de la partida**: stats propios y de los 10 jugadores (KDA, CS, items, nivel) vía Live Client Data API.
- **Coach de IA en 5 momentos clave**:
  1. `CHAMP_SELECT_END` — análisis especulativo de matchup y composición.
  2. `LIVE_CLIENT_MATCHUP` — análisis de matchup con roles confirmados.
  3. `DEATH` — análisis de cada muerte propia (con screenshot, zona del mapa, si había gank o visión).
  4. `OBJECTIVE_SPAWN` — aviso pre-spawn de objetivos épicos, 30s antes del spawn estimado:
     - Dragón: primer spawn 5:00, respawn cada +5 min.
     - Heraldo: primer spawn 14:00, respawn +6 min.
     - Barón: primer spawn 20:00, respawn +6 min.
     - Larvas del vacío (Voidgrubs): spawn único a los 5:30 (verificado en código: ventana 330-335s).
  5. `GAME_END` — resumen post-partida con áreas de mejora.
- **Overlay in-game transparente y click-through** (no bloquea el mouse/juego), con auto-show al entrar en partida y toggle manual (F5).
- **Text-to-Speech** opcional de los consejos del coach (actualmente deshabilitado por config).
- **API REST local** (puerto 8080) que expone todo el estado interno para debug (`/api/lcu/*`, `/api/live-client/*`, `/api/champions`, `/api/matches/*`, `/api/summoner/*`, `/api/live-game/*`).
- **Sesión persistente con gestión completa**: al buscar un invocador se guarda (nombre, tag, puuid, ícono, nivel) en H2; se restaura automáticamente al reabrir la app (`@PostConstruct` en `GameStateService`); botón de "Cerrar sesión" que limpia H2 y la UI al instante; al cambiar de sesión se limpian los datos viejos antes de cargar los nuevos (evita mezclar estado de dos invocadores distintos).

---

## 5.1 Detalle de UI (tema oscuro estilo GitHub Dark)

- **Tema**: dark theme completo vía CSS con variables, estilo similar a GitHub Dark.
- **Header de la ventana principal**: logo de PowerSpike + indicador de estado (bolita de color: verde/roja/azul/naranja según la fase del juego) + bloque de sesión activa (foto de perfil, nombre, nivel, botón "Cerrar sesión").
- **4 tabs**: Perfil, Champ Select, Partida, Coach IA.
  - **Perfil**: buscador de invocador + historial de partidas (cards con campeón, KDA en verde/rojo según performance, W/L, duración, ítems, tiempo relativo tipo "hace 1h" / "hace 2d").
  - **Champ Select**: picks, bans y timer en vivo actualizados vía WebSocket del LCU.
  - **Partida**: stats del jugador propio (KDA, CS, oro) + los dos equipos de 10 jugadores con sus campeones.
  - **Coach IA**: resultado del análisis actual + historial acumulado de análisis previos.

## 5.2 Detalle del Overlay

- Implementado con **2 stages JavaFX** separados: una *control bar* clickeable (botón cerrar "✕" + drag handle) y el *overlay* propiamente dicho (click-through vía JNA, `WS_EX_TRANSPARENT | WS_EX_LAYERED`).
- **Alto dinámico**: se expande o colapsa según el largo del consejo mostrado, con un máximo de **700px**.
- **Posicionamiento**: esquina superior derecha de la pantalla (top-right), arrastrable — el drag se calcula en base a coordenadas absolutas de pantalla (no relativas al nodo), y al mover la barra de control se mueve el overlay principal junto con ella.
- **Visibilidad temporal**: el consejo queda visible 15 segundos y luego hace fade-out de 300ms.
- **Auto-show/hide**: se muestra automáticamente cuando la fase de juego pasa a `InProgress` y se oculta al salir de esa fase; también hay toggle manual con **F5**.

---

## 6. Integraciones Externas

| Integración | Uso | Autenticación |
|---|---|---|
| **League Client Update (LCU)** local (`127.0.0.1:{puerto dinámico}`) | Fase de juego, champ select (REST + WebSocket) | Basic auth (`riot:token`) extraído del lockfile del proceso |
| **Live Client Data API** (`127.0.0.1:2999`) | Datos de la partida en curso (stats, eventos, jugador activo) | Sin auth (solo accesible localmente durante una partida activa) |
| **Riot Games API** (`la2.api.riotgames.com`, `americas.api.riotgames.com`) | Cuentas, summoner, spectator (partida activa), historial de partidas (Match-V5) | Header `X-Riot-Token` (API key) |
| **Data Dragon** (CDN estático de Riot) | Catálogo de campeones e ítems (nombres, iconos, versión del patch) | Sin auth |
| **OpenAI API** (`api.openai.com`) | Chat Completions (texto y multimodal con imágenes) para el análisis del coach, y TTS para la voz | API key (Bearer) |

---

## 7. Persistencia (H2 embebida, `data/powerspike.trace.db`)

Configuración: `spring.datasource.url=jdbc:h2:file:./data/powerspike`, `ddl-auto=update` (sin Flyway/Liquibase).

| Entidad | Propósito |
|---|---|
| `ActiveSession` (fila única, id fijo=1) | Persiste la sesión activa del invocador (puuid, riotId, ícono, nivel) entre reinicios de la app. |
| `SummonerEntity` (PK = puuid) | Caché de datos de invocador con TTL de 5 minutos, para reducir llamadas a la Riot API. |
| `MatchEntity` (PK = matchId) | Caché permanente de partidas ya procesadas (stats, items, campeones enemigos), evita reconsultar Match-V5. |

---

## 8. Estructura del Proyecto

```
PowerSpike/
├── build.gradle              # Config de build, dependencias, plugin JavaFX
├── data/powerspike.trace.db  # Base de datos H2
└── src/main/
    ├── java/com/cuevas/powerspike/
    │   ├── PowerspikeApplication.java   # Entry point Spring Boot
    │   ├── config/           # RestTemplates, SSL laxo (LCU/Live Client), Jackson
    │   ├── controller/       # API REST local (champions, lcu, live-client, live-game, matches, summoner)
    │   ├── dto/               # DTOs de todas las integraciones externas
    │   ├── exception/         # Excepciones de negocio + handler global
    │   ├── model/             # Entidades JPA (ActiveSession, MatchEntity, SummonerEntity)
    │   ├── repository/        # Spring Data JPA repositories
    │   ├── service/           # Lógica de negocio: LCU, Live Client, Riot API, Data Dragon
    │   │   └── analysis/      # Motor de IA: engine, prompts, OpenAI, screenshots, TTS, zonas del mapa
    │   └── ui/                # JavaFX: MainController + overlay (stage, controlbar, click-through)
    └── resources/
        ├── application.properties
        ├── css/                # Estilos de la ventana principal y del overlay
        └── fxml/               # main-view.fxml (4 tabs) y overlay.fxml (minimalista)
```

---

## 9. Consideraciones y Deuda Técnica Detectadas

- **API keys en texto plano (riesgo controlado)**: `src/main/resources/application.properties` contiene la API key de Riot Games y la de OpenAI hardcodeadas en texto plano. El archivo **ya está en `.gitignore`**, por lo que no hay exposición vía git. Como mejora planeada, se migrarán a un `.env` (por ejemplo con `spring-dotenv` o variables de entorno del sistema + placeholders `${RIOT_API_KEY}` / `${OPENAI_API_KEY}` en el properties). Buena práctica a mantener: nunca sacar el archivo del `.gitignore` ni commitear valores reales por accidente (p. ej. al copiar/pegar en otro archivo versionado).
- **Dependencia de Windows**: `LcuLockfile` usa `wmic` (deprecado en versiones recientes de Windows) y `OverlayClickThrough` usa la API Win32 vía JNA — la app **no es multiplataforma**.
- **SSL laxo**: las conexiones al LCU y al Live Client Data API aceptan cualquier certificado (`TrustStrategy` siempre `true`). Es una decisión aceptable porque son endpoints locales autofirmados de Riot, pero conviene documentarlo explícitamente como tal (ya está limitado a `127.0.0.1`).
- **Sin migraciones de esquema**: `ddl-auto=update` sin Flyway/Liquibase — riesgo en evolución de esquema a futuro.
- **Parsing manual de JSON en `OpenAIClient`**: la respuesta de OpenAI se parsea con manipulación de strings en vez de un DTO/Jackson, es frágil ante cambios de formato de la API.
- **Modelo de IA no estándar**: se referencia el modelo `gpt-5.6-luna` (nombre inusual, posible alias interno o error), con inconsistencia respecto al texto mostrado en la UI (`gpt-5.4-mini`). Conviene verificar cuál es el modelo real vigente.
- **README vacío**: el `README.md` del proyecto no tiene contenido.
- **Nombre de archivo H2**: hay una referencia previa a `data/powerspike.mv.db` (archivo de datos principal que genera H2) mientras que en este checkout solo se ve `data/powerspike.trace.db` (archivo de trace/log). Ambos son generados por H2 a partir del mismo `spring.datasource.url=jdbc:h2:file:./data/powerspike`; si falta el `.mv.db` en el repo probablemente esté igual en `.gitignore` (es el archivo con los datos reales).

---

## 11. Detalles adicionales de infraestructura

- **Jackson 3.x**: el proyecto usa el paquete `tools.jackson` (Jackson 3, no `com.fasterxml.jackson` clásico), sin anotaciones en los DTOs, con `FAIL_ON_NULL_FOR_PRIMITIVES=false` configurado (tolerante a nulls en tipos primitivos al deserializar respuestas externas).
- **Logging**: uso consistente de SLF4J en todo el código de servicios (no se usa `System.out.println`).
- **Java 24 + Gradle 9.5**: se aprovechan features modernas del lenguaje (records, pattern matching) en DTOs y lógica de análisis.
- **Estado de verificación (checkpoint de una sesión previa de desarrollo)**: compilación exitosa (`BUILD SUCCESSFUL`), 0 errores reportados por inspecciones de IntelliJ, y prueba manual end-to-end confirmada (champ select, muertes, post-game, overlay funcionando). Esto es un snapshot de en qué estado quedó el proyecto en un momento dado, no un pipeline de CI automatizado — no hay tests unitarios/integración más allá del test de contexto por defecto de Spring Boot (`PowerspikeApplicationTests`).

---

## 10. Cómo Ejecutar

```powershell
# Desde la carpeta PowerSpike/
.\gradlew.bat bootRun
```

Requiere:
- Java 24 (toolchain configurado en Gradle, se descarga automáticamente si no está disponible).
- Windows (por las dependencias de `wmic` y JNA/Win32).
- League of Legends cliente instalado para las funcionalidades de LCU/Live Client.
- Una API key válida de Riot Games y de OpenAI configuradas en `application.properties`.
