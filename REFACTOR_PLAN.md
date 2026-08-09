# PowerSpike — Plan de Refactor: Separación Backend / Frontend

> Este documento describe el plan de refactorización **antes de implementarlo**. No se modifica código en este paso. Sirve como especificación de referencia durante la migración.

## 0. Contexto y decisiones ya tomadas

Se decidió, en conjunto con el usuario, el siguiente diseño:

| Decisión | Resultado |
|---|---|
| ¿Quién detecta CUÁNDO disparar un análisis de IA? | **El frontend** (ya está polleando LCU/Live Client, tiene los datos y la latencia más baja). El backend queda *stateless* respecto a triggers: solo arma el prompt y llama a OpenAI cuando el frontend se lo pide. |
| ¿Cómo se comunican? | **REST síncrono** (request → response). No hace falta WebSocket porque el frontend siempre inicia la conversación (ya tiene los datos locales antes de pedir el análisis). |
| ¿Dónde vive la sesión del invocador (login/logout)? | **Archivo local del frontend** (JSON), sin base de datos por ahora. A futuro se reemplazará por un sistema de cuentas propio de PowerSpike + login con Riot, pero eso queda fuera de este refactor. |
| ¿Cómo se organiza el proyecto? | **2 módulos Gradle, 2 procesos independientes**: `backend` (Spring Boot headless) y `frontend` (JavaFX). Se agrega un tercer módulo `common` con los contratos compartidos (DTOs de la API interna entre ambos). |
| ¿Dónde se aloja el backend? | **En la nube** (SaaS). El **frontend es lo único que se empaqueta y distribuye** al usuario final. El backend queda alojado en un proveedor cloud (se definirá cuál más adelante). |
| ¿Qué modelo de IA se usa? | **GPT Luna 5.6** (modelo multimodal que soporta imágenes). Esto es **crucial para la detección de muertes**: el análisis de cada muerte incluye una captura de pantalla que el modelo interpreta visualmente. El backend siempre llama a este modelo para el chat y el análisis con imagen. |
| ¿Autenticación frontend ↔ backend? | **A futuro**. Hoy solo el dueño de la app la usa, así que por el momento el backend expuesto en la nube no requiere login. Está anotado como pendiente: cuando haya más de un usuario, se agregará autenticación (API key por instalación o login + JWT) para que nadie más pueda consumir el backend y gastar créditos de OpenAI. |
| Principio rector del split | El **frontend** solo habla con procesos **locales del propio juego** (LCU en puerto dinámico, Live Client Data API en `127.0.0.1:2999`) — no necesita ningún secreto. Todo lo que requiere la **API key de Riot** o la **API key de OpenAI** vive exclusivamente en el **backend**. El backend se conecta a OpenAI y a Riot desde la nube; los screenshots de las muertes viajan tal cual (en base64) desde el frontend al backend remoto. |

---

## 1. Arquitectura resultante

```
┌────────────────────────────── Frontend (proceso JavaFX) ──────────────────────────────┐
│                          ═══ ÚNICO COMPONENTE DISTRIBUIDO ═══                          │
│  ui/ (MainController, Overlay*)         GameStateService (in-memory, JavaFX Properties)│
│         ▲                                        ▲            │                        │
│         │                                        │            │ detecta triggers        │
│         │                             ┌──────────┴──────┐    (muerte, objetivo, etc.)  │
│         │                             │ TriggerDetector   │────────────┐               │
│         │                             │ (ex-AnalysisEngine)│           │               │
│         │                             └──────────┬────────┘           ▼               │
│  LocalSessionStore (archivo JSON)                 │             AnalysisApiClient       │
│         ▲                                          ▼                   │               │
│         │                              ScreenshotService (Robot)       │               │
│         │                                                              │               │
│  LcuLockfile → LcuApi → LcuWebSocket (LCU local, puerto dinámico)      │               │
│  LiveClientApi → LiveGamePollingService (Live Client, 127.0.0.1:2999)  │               │
│                                                                          │               │
│  BackendApiClient (champions, matches, summoner, live-game/spectator) ─┼───┐           │
└──────────────────────────────────────────────────────────────────────────┼───┼───────────┘
                                                                             │   │
                                          HTTPS (backend URL configurable: dev =
                                        http://localhost:8080, prod = URL de la nube)
                                                                             │   │
┌────────────────────────────── Backend (proceso Spring Boot headless) ────▼───▼──────────┐
│                          ═══ ALOJADO EN LA NUBE (SaaS) ═══                               │
│  AnalysisController          MatchController / SummonerController / ChampionController /  │
│       │                      LiveGameController (spectator)                               │
│       ▼                                                                                    │
│  AnalysisService (ex-AnalysisEngine, sin listeners) ──▶ PromptBuilder ──▶ OpenAIClient     │
│       │                                                       │              │            │
│       ▼                                                MapZoneClassifier   TtsClient       │
│  RiotApiClient ──▶ Riot Games API (Account/Summoner/Spectator/Match-V5)                    │
│  DataDragonClient ──▶ Data Dragon CDN                                                       │
│  MatchService / SummonerRepository / MatchRepository ──▶ H2 (caché, sin datos de sesión)   │
│                                                                                            │
│  application.properties (riot.api.key, openai.api.key) — nunca sale de este proceso        │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

**Regla de oro**: el frontend nunca tiene ni ve las API keys. Todo lo que las requiere pasa por un endpoint del backend, que ahora además está **en la nube** (la API key de OpenAI y la de Riot viven solo en el servidor). El frontend distribuye únicamente la UI + la lógica local del juego; sin backend no hay análisis de IA (pero el frontend igual puede mostrar la partida en vivo localmente).

---

## 2. Módulos Gradle

### 2.1 `common` (nuevo módulo, librería simple, sin Spring Boot ni JavaFX)

Contiene únicamente **contratos compartidos** (records/DTOs Java planos) que viajan por la API REST entre frontend y backend. Sin lógica de negocio.

- DTOs de datos crudos del juego que el frontend reenvía al backend para armar prompts:
  - `LiveClientAllDataDTO`, `LiveClientEventDTO`, `LiveClientEventsDTO`, `LiveClientPlayerDTO`, `LiveClientActivePlayerDTO`, `LiveClientPositionDTO`, `LiveClientScoresDTO`, `LiveClientItemDTO`, `LiveClientRunesDTO`, `LiveClientRuneTreeDTO`, `LiveClientAbilitiesDTO`, `LiveClientAbilityDTO`, `LiveClientChampionStatsDTO`, `LiveClientSummonerSpellDTO`, `LiveClientSummonerSpellsDTO`, `LiveClientSummaryDTO`.
  - `LcuChampSelectDTO`, `LcuTeamMemberDTO`, `LcuBanDTO`, `LcuBansDTO`, `LcuTimerDTO`.
- Contrato de la API de análisis (nuevo, no existía antes):
  - `AnalysisTrigger` (enum: `CHAMP_SELECT_END`, `LIVE_CLIENT_MATCHUP`, `DEATH`, `OBJECTIVE_SPAWN`, `GAME_END`).
  - `ChampSelectAnalysisRequest`, `LiveClientMatchupAnalysisRequest`, `DeathAnalysisRequest` (incluye `screenshotBase64` opcional), `ObjectiveSpawnAnalysisRequest`, `GameEndAnalysisRequest`.
  - `AnalysisResponse` (trigger, texto de respuesta, éxito/error, `audioBase64` opcional si TTS está habilitado).
- DTOs de Riot/Data Dragon que ya se comparten hoy vía los controllers existentes (se mueven tal cual desde `dto/`): `AccountDTO`, `SummonerDTO`, `MatchDTO`, `MatchListDTO`, `MatchParticipantDTO`, `MatchSummaryDTO`, `ChampionData`, `ChampionInfo`, `ItemData`, `ChampionWinrateDTO`, `CurrentGameInfo`, `CurrentGameParticipant`, `GameCustomizationObject`, `BannedChampion`, `Observer`, `Perks`.

> Nota: `common` es una dependencia de compilación tanto de `backend` como de `frontend`. Ninguno de los dos la referencia al revés.

### 2.2 `backend` (Spring Boot, **headless**, sin JavaFX ni dependencias de UI)

Responsabilidades: todo lo que requiere la API key de Riot, todo lo que requiere la API key de OpenAI, y la persistencia de caché (H2). **Se despliega en la nube** como un servicio standalone (su propio `bootJar`, headless). El frontend le habla vía HTTPS.

**Se queda / se mueve tal cual (con imports actualizados a `common` donde corresponda):**
- `service/RiotApiClient.java`
- `service/DataDragonClient.java`
- `service/MatchService.java`
- `service/analysis/PromptBuilder.java`
- `service/analysis/MapZoneClassifier.java`
- `service/analysis/OpenAIClient.java`
- `service/analysis/TtsClient.java`
- `controller/ChampionController.java`
- `controller/MatchController.java`
- `controller/SummonerController.java`
- `controller/LiveGameController.java` (spectator-v5, depende de Riot API)
- `model/MatchEntity.java`, `model/SummonerEntity.java`
- `repository/MatchRepository.java`, `repository/SummonerRepository.java`
- `exception/GameNotActiveException.java`, `exception/SummonerNotFoundException.java`, `exception/GlobalExceptionHandler.java`
- `config/RestTemplateConfig.java`, `config/JacksonConfig.java`
- `application.properties` (con `riot.api.key`, `riot.platform-url`, `riot.account-url`, `openai.api.key`, `openai.tts.enabled`, `server.port=8080`, `spring.datasource.url`)

**Se elimina de este módulo (ya no aplica, pertenecen al frontend):**
- `config/LcuRestTemplateConfig.java`, `config/SslConfig.java` (SSL laxo era para hablar con LCU/Live Client, que ahora vive en el frontend)
- `model/ActiveSession.java`, `repository/ActiveSessionRepository.java` (la sesión pasa a ser un archivo local del frontend)
- `service/LcuApi.java`, `service/LcuLockfile.java`, `service/LcuWebSocket.java`
- `service/LiveClientApi.java`, `service/LiveGamePollingService.java`
- `service/GameStateService.java` (se reemplaza por un equivalente en el frontend, sin dependencias de Spring Data)
- `controller/LcuController.java`, `controller/LiveClientController.java` (eran fachadas de debug sobre datos locales; ahora esos datos ni siquiera llegan al backend salvo dentro de un `AnalysisRequest`)

**Se crea nuevo:**
- `service/analysis/AnalysisService.java` — reemplaza a `AnalysisEngine.java`, pero **sin** listeners de `GameStateService` ni cooldowns ni timers. Expone métodos síncronos tipo:
  - `AnalysisResponse analyzeChampSelect(ChampSelectAnalysisRequest req)`
  - `AnalysisResponse analyzeLiveClientMatchup(LiveClientMatchupAnalysisRequest req)`
  - `AnalysisResponse analyzeDeath(DeathAnalysisRequest req)`
  - `AnalysisResponse analyzeObjectiveSpawn(ObjectiveSpawnAnalysisRequest req)`
  - `AnalysisResponse analyzeGameEnd(GameEndAnalysisRequest req)`
  
  Internamente arma el `AnalysisContext`/prompt igual que antes (reutilizando `PromptBuilder` y `MapZoneClassifier`), llama a `OpenAIClient` con el modelo **GPT Luna 5.6** (que soporta imágenes), y si `openai.tts.enabled=true` también llama a `TtsClient` y devuelve el audio en base64 dentro de la respuesta (en vez de reproducirlo directamente, porque el backend no tiene salida de audio del usuario — la reproducción es responsabilidad del frontend).
- `controller/AnalysisController.java` — expone los 5 métodos de arriba como endpoints `POST`:
  - `POST /api/analysis/champ-select`
  - `POST /api/analysis/matchup`
  - `POST /api/analysis/death` (acepta `screenshotBase64`, el backend lo envía al modelo multimodal GPT Luna 5.6)
  - `POST /api/analysis/objective-spawn`
  - `POST /api/analysis/game-end`

**Persistencia resultante en H2**: solo `SummonerEntity` (caché TTL 5 min) y `MatchEntity` (caché permanente). Ya no incluye `ActiveSession`.

> **Despliegue**: el backend se aloja en la nube. Como hoy no hay autenticación, el endpoint está expuesto públicamente — se acepta mientras lo use solo el dueño de la app. Las API keys (`riot.api.key`, `openai.api.key`) deben inyectarse como **variables de entorno** en el entorno de nube (no en un archivo versionado), aunque el `application.properties` local del backend siga usándolas con placeholder `${RIOT_API_KEY}` / `${OPENAI_API_KEY}`.

### 2.3 `frontend` (JavaFX, standalone)

Responsabilidades: todo lo que habla con procesos locales del juego (LCU, Live Client Data API), la UI, el overlay, la detección de triggers, la captura de pantalla, y el consumo de la API del backend.

**Se queda tal cual (con configuración SSL laxa incluida, porque sigue hablando con LCU/Live Client localmente):**
- `service/LcuLockfile.java`, `service/LcuApi.java`, `service/LcuWebSocket.java`
- `service/LiveClientApi.java`, `service/LiveGamePollingService.java`
- `config/LcuRestTemplateConfig.java`, `config/SslConfig.java`
- `service/analysis/ScreenshotService.java`
- `ui/JavaFxApplication.java`, `ui/MainController.java`, `ui/overlay/*` (`OverlayStage`, `OverlayControlBar`, `OverlayController`, `OverlayClickThrough`)
- `resources/fxml/*`, `resources/css/*`

**Se adapta:**
- `service/GameStateService.java` — se mantiene como el estado en memoria (JavaFX `Properties`) que consume la UI, pero:
  - Ya no persiste nada en H2 (se elimina la dependencia de `ActiveSessionRepository`); en su lugar usa el nuevo `LocalSessionStore`.
  - Incorpora la lógica de **detección de triggers** que antes vivía en `AnalysisEngine` (ver punto siguiente).

**Se crea nuevo:**
- `service/TriggerDetector.java` (o se integra directamente en `GameStateService`) — contiene la lógica que hoy está en `AnalysisEngine`: detectar fin de champ select, primera conexión del Live Client, muertes propias (con cooldown de 30s), spawns de objetivos próximos (Dragón/Heraldo/Barón/Larvas), y fin de partida. En vez de llamar a `OpenAIClient` directamente, arma el DTO de request correspondiente (`DeathAnalysisRequest`, etc.) y se lo pasa a `AnalysisApiClient`.
- `service/AnalysisApiClient.java` — cliente HTTP (`RestTemplate` o `RestClient`) que llama a los 5 endpoints de `AnalysisController` en el backend. Devuelve `AnalysisResponse`, que se publica en la misma `ObjectProperty<AnalysisResult>` que hoy consumen `MainController` y `OverlayController` (se mantiene el contrato de UI intacto). Si el backend no responde (no está levantado, timeout, etc.), devuelve un resultado de error controlado en vez de tirar excepción — la UI debe poder mostrar "Backend no disponible" sin romperse.
- `service/BackendApiClient.java` — cliente HTTP para los endpoints que ya existían en el backend y que el frontend necesita consumir en vez de tener el bean inyectado directamente: catálogo de campeones/items (`/api/champions`), historial y winrates (`/api/matches/...`), datos de invocador (`/api/summoner/...`), partida en vivo por Riot ID vía spectator (`/api/live-game/...`).
- `service/LocalSessionStore.java` — reemplaza a `ActiveSession`/`ActiveSessionRepository`. Persiste en un archivo JSON local (ej. `%APPDATA%/PowerSpike/session.json` en Windows, o `./data/session.json` relativo al ejecutable) los campos: `gameName`, `tagLine`, `puuid`, `profileIconId`, `summonerLevel`. Expone `save(...)`, `load()`, `clear()`. Se usa en `@PostConstruct` de `GameStateService` para restaurar la sesión al abrir la app, igual que hoy, pero sin H2/JPA de por medio.

**Se elimina de este módulo:**
- Toda dependencia de Spring Data JPA / H2 (el frontend no necesita base de datos).
- `service/analysis/AnalysisEngine.java` se **divide**: la parte de detección de triggers pasa a `TriggerDetector`/`GameStateService` (frontend), la parte de armado de prompt + llamada a OpenAI pasa a `AnalysisService` (backend).
- `service/analysis/PromptBuilder.java`, `MapZoneClassifier.java`, `OpenAIClient.java`, `TtsClient.java` — se van al backend (ver 2.2). El frontend ya no los necesita directamente.
- `service/RiotApiClient.java`, `service/DataDragonClient.java`, `service/MatchService.java` — se van al backend. El frontend los reemplaza por llamadas a `BackendApiClient`.

**Config nueva del frontend** (`application.properties` propio, sin secretos):
```properties
spring.application.name=powerspike-frontend
# URL del backend: localhost:8080 en dev, URL de la nube en producción (se inyecta al empaquetar/distribuir)
backend.base-url=http://localhost:8080
```
El `backend.base-url` se lee en tiempo de ejecución (via `@Value("${backend.base-url:http://localhost:8080}")`), de modo que **no se hardcodea la URL de la nube en el código**: se inyecta al armar el ejecutable de distribución.

**Nota sobre TTS en el frontend**: cuando `AnalysisResponse` viene con `audioBase64` (backend generó el audio), el frontend decodifica el base64, lo guarda en un archivo temporal y lo reproduce con `javafx.scene.media.MediaPlayer` — misma lógica que hoy tiene `TtsClient.speak()`, pero sin necesidad de la API key (el audio ya viene generado).

---

## 3. Contrato REST entre frontend y backend

### 3.1 Endpoints ya existentes (se mueven al backend sin cambios de contrato)

| Método | Endpoint | Usa Riot API / Data Dragon |
|---|---|---|
| GET | `/api/champions` , `/api/champions/{id}` | Data Dragon |
| GET | `/api/summoner/{gameName}/{tagLine}` | Riot API (Account + Summoner) |
| GET | `/api/matches/{gameName}/{tagLine}?count=20` | Riot API (Match-V5) + caché H2 |
| GET | `/api/live-game/{gameName}/{tagLine}` | Riot API (Spectator-V5) |

### 3.2 Endpoints nuevos (motor de IA)

| Método | Endpoint | Body (desde `common`) | Devuelve |
|---|---|---|---|
| POST | `/api/analysis/champ-select` | `ChampSelectAnalysisRequest` | `AnalysisResponse` |
| POST | `/api/analysis/matchup` | `LiveClientMatchupAnalysisRequest` | `AnalysisResponse` |
| POST | `/api/analysis/death` | `DeathAnalysisRequest` (incluye `screenshotBase64` opcional) | `AnalysisResponse` |
| POST | `/api/analysis/objective-spawn` | `ObjectiveSpawnAnalysisRequest` | `AnalysisResponse` |
| POST | `/api/analysis/game-end` | `GameEndAnalysisRequest` | `AnalysisResponse` |

### 3.3 Endpoints que se dan de baja (eran de debug sobre datos locales, ya no tienen sentido en el backend)

`/api/lcu/*`, `/api/live-client/*` — si se necesitan para debug, se pueden re-implementar como endpoints locales dentro del propio **frontend** (que sí tiene esos datos), no en el backend.

---

## 4. Flujo de la aplicación (post-refactor, end-to-end)

1. **Arranque**: 
   - `backend`: alojado en la nube, ya corriendo de forma independiente (o en dev con `./gradlew :backend:bootRun` en localhost:8080).
   - `frontend`: se ejecuta el único artefacto distribuido (`./gradlew :frontend:run`). Al arrancar, `LocalSessionStore` restaura la sesión guardada (si existe) desde el archivo JSON local.
2. **Detección del cliente de LoL**: igual que hoy, `LcuLockfile` (frontend) detecta `LeagueClientUx.exe` y `LcuApi` (frontend) hace polling de `gameflow-phase` directo contra el LCU local — **sin pasar por el backend**.
3. **Champ Select**: `LcuWebSocket` (frontend) recibe eventos en tiempo real. Al finalizar, `TriggerDetector` arma un `ChampSelectAnalysisRequest` con los datos crudos (picks, bans, roles) y se lo manda al backend vía `AnalysisApiClient` → `POST /api/analysis/champ-select`. El backend arma el prompt (`PromptBuilder`), llama a OpenAI (GPT Luna 5.6) y devuelve el consejo.
4. **Partida en curso**: `LiveGamePollingService` (frontend) sigue polleando el puerto 2999 directamente. Al conectar por primera vez, se dispara `POST /api/analysis/matchup` con los roles reales.
5. **Muerte del jugador**: detectada en el frontend (mismo algoritmo de hoy, con cooldown de 30s). El frontend captura la pantalla (`ScreenshotService`, JavaFX `Robot`) y arma un `DeathAnalysisRequest` con el estado del juego + la imagen en base64, y lo manda a `POST /api/analysis/death`. El backend construye el contexto (incluida la clasificación de zona del mapa vía `MapZoneClassifier`, que ahora corre server-side sobre los datos que le llegan), llama a OpenAI **con imagen** (GPT Luna 5.6 multimodal) y devuelve el consejo.
6. **Objetivo por spawnear**: el frontend calcula los timers (igual que hoy) y dispara `POST /api/analysis/objective-spawn` 30s antes del spawn estimado.
7. **Fin de partida**: el frontend detecta la transición de fase y dispara `POST /api/analysis/game-end` con el resumen de los 10 jugadores.
8. **Presentación del feedback**: en todos los casos, la respuesta (`AnalysisResponse`) se recibe en el frontend y se publica en la misma `ObjectProperty<AnalysisResult>` de siempre → se muestra en la tab "Coach" y en el overlay transparente click-through, con TTS opcional (si el backend generó audio).
9. **Búsqueda de invocador / historial**: la tab "Perfil" llama a `BackendApiClient` (`/api/summoner/...`, `/api/matches/...`), que a su vez llama a la Riot API (desde la nube) y cachea en H2 **del backend**. Al encontrar un invocador, el frontend guarda esos datos en `LocalSessionStore` (archivo local) para la próxima vez que se abra la app.
10. **Resiliencia**: si el backend no está disponible (no responde en la nube, timeout, red caída), el frontend debe seguir funcionando para todo lo que no depende de él (ver LCU/Live Client en vivo), mostrando un estado de error solo en las secciones que dependen del backend (Coach IA, Perfil, Historial).

---

## 5. Formato del archivo de sesión local (frontend)

```json
{
  "gameName": "Ejemplo",
  "tagLine": "LAN",
  "puuid": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "profileIconId": 4568,
  "summonerLevel": 214
}
```
Ubicación propuesta: `./data/session.json` relativo al directorio de trabajo del frontend (consistente con cómo hoy se guarda `data/powerspike.trace.db` relativo al proyecto). Se puede migrar más adelante a `%APPDATA%/PowerSpike/` si se empaqueta como instalador.

---

## 6. Orden de migración propuesto (para minimizar riesgo)

1. Crear el módulo `common` con los DTOs compartidos (copiar, no mover, para no romper el proyecto actual todavía).
2. Crear el módulo `backend` a partir del proyecto actual: copiar todo excepto lo que se identificó como "frontend-only", quitar JavaFX del `build.gradle`, crear `AnalysisService` + `AnalysisController` reutilizando `PromptBuilder`/`OpenAIClient`/`TtsClient`/`MapZoneClassifier` ya existentes (solo cambia quién los invoca). Verificar que compila y que los endpoints existentes (`/api/champions`, `/api/matches`, `/api/summoner`, `/api/live-game`) siguen funcionando igual.
3. Crear el módulo `frontend` a partir del proyecto actual: quitar Spring Data JPA/H2, quitar `RiotApiClient`/`DataDragonClient`/`MatchService`/`PromptBuilder`/`MapZoneClassifier`/`OpenAIClient`/`TtsClient`/`AnalysisEngine`, crear `LocalSessionStore`, `BackendApiClient`, `AnalysisApiClient`, y mover la lógica de triggers de `AnalysisEngine` a `GameStateService`/`TriggerDetector`.
4. Actualizar `MainController` y `OverlayController` para consumir los nuevos clientes en vez de los beans que ya no existen en este módulo.
5. Probar el flujo completo con ambos procesos corriendo (`backend` primero, después `frontend`), validando los 5 triggers de IA (incluida la muerte con screenshot vía GPT Luna 5.6).
6. Definir el despliegue del backend en la nube (proveedor + cómo se inyectan las API keys como env vars) y validar el acceso desde el frontend con `backend.base-url` apuntando a la nube.
7. Borrar el proyecto monolítico original (`PowerSpike/` actual) una vez validado, o dejarlo como referencia histórica hasta confirmar que el split funciona igual de bien.
8. Actualizar `settings.gradle` en la raíz para incluir los 3 módulos (`common`, `backend`, `frontend`).
9. Actualizar `SPECIFICATIONS.md` para reflejar la nueva arquitectura una vez completado el split.

---

## 7. Riesgos / cosas a decidir más adelante (fuera de alcance de este refactor)

- **Autenticación entre frontend y backend**: hoy el backend está expuesto en la nube **sin autenticación**, porque solo lo usa el dueño de la app. En cuanto haya más de un usuario, hay que agregar autenticación (API key por instalación o login + JWT) para que nadie externo pueda consumir el backend y gastar créditos de OpenAI. Queda como pendiente explícito.
- **Elección del proveedor cloud y despliegue del backend**: definir dónde se aloja (Railway, Render, Fly.io, AWS, etc.), cómo se inyectan `RIOT_API_KEY` y `OPENAI_API_KEY` como env vars, y cómo se hace el CORS/servicio expuesto. Se resuelve en el paso 6 del plan de migración.
- **Manejo de fallos del backend**: definir UX exacta cuando el backend no está disponible (reintentos, mensajes en la UI, deshabilitar tabs).
- **Empaquetado/distribución**: **solo se distribuye el frontend** (decisión tomada). Falta definir el formato: ¿`.exe` instalador (jpackage), imagen portable, o zip? Se resuelve después de validar el split funcional.
- **Sistema de cuentas propio + login con Riot** (mencionado por el usuario): quedará para una iteración posterior; este refactor solo prepara el terreno (session en archivo local, fácil de reemplazar después por un login real contra el futuro backend de cuentas).
- **Versión del modelo de IA**: confirmado que es **GPT Luna 5.6** (multimodal, con soporte de imágenes). Se resuelve la inconsistencia previa del texto mostrado en la UI (`gpt-5.4-mini`) — hay que alinearlo con `gpt-5.6-luna` en la interfaz.
- **Tamaño/latencia de los screenshots**: los screenshots en base64 viajan tal cual al backend remoto (decisión tomada). Si la latencia o el costo sube en uso real, se puede optimizar después (compresión, menor resolución, envío por chunks).
