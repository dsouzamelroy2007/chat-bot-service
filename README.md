# chat-bot-service

A self-hostable Spring Boot microservice that gives AI-generated chat replies, routed across several free-tier LLM providers (Gemini, Groq, Cerebras, Mistral, OpenRouter) via Spring AI so it can run at zero API cost. Ships with a minimal static chat widget for trying it out in a browser, and exposes its REST API via Swagger/OpenAPI for a real front-end app to integrate against.

## Tech stack

| Category | Technologies |
|---|---|
| Frameworks | Spring Boot 4.1 · Spring AI (multi-provider, free-tier-first) · Resilience4j · springdoc-openapi |
| Testing | JUnit · Mockito |
| Infrastructure | Docker · Maven |
| Languages | Java 21 · Bash |

## Getting started

**Prerequisites:** Maven, Java 21, Docker (for Postgres/Redis, see below).

For real (non-stubbed) replies, set at least one free-tier provider's API key as an environment variable before starting the application — see [.env.example](.env.example) for the full list, where to get each one for free, and every other environment variable the app reads:

```bash
export GEMINI_API_KEY=...
```

With no key set at all, the app still boots and responds, just with a canned fallback reply instead of a real one (see [Live testing without an API key](#live-testing-without-an-api-key) below for a cost-free way to exercise the full request flow).

The default profile also requires Postgres and Redis to be reachable at boot (`spring-boot-starter-data-jpa`/`-data-redis` back conversation memory, see below). The quickest way to get both locally is `docker-compose up -d postgres redis`. The cost-free `local` profile below needs neither.

## Running the application

**Option 1 — Docker Compose.** After cloning, from the project directory:

```bash
cp .env.example .env   # fill in whichever keys you have
sh start.sh
```

This runs the test suite, builds a Docker image, and starts the full stack (app + Postgres + Redis) via `docker-compose`, which reads `.env` automatically.

**Option 2 — From an IDE.** Start Postgres and Redis (`docker-compose up -d postgres redis`), import the project (Maven), build it, and set whichever provider key(s) you have (and, if `5432`/`6379` are already taken locally, `DB_HOST_PORT`/`REDIS_HOST_PORT`) in the run configuration's environment variables before running `ChatBotService`.

Once running, the service listens on `http://localhost:8080/bot`.

## Chat widget

A minimal static chat widget ([src/main/resources/static/index.html](src/main/resources/static/index.html)) is served by the same application at `http://localhost:8080/bot/`. It's a single self-contained HTML page — no build step, no separate frontend project — that calls `POST /chat/reply` and renders the conversation.

![Chat widget showing a user message and the bot's reply](docs/screenshots/chat-widget.png)

It's meant as a quick way to try the bot in a browser, not as a production UI. The `POST /chat/reply` endpoint remains fully documented via Swagger/OpenAPI (see below) so a real, separately-hosted front end can integrate against it directly.

## Testing

### Unit tests

```bash
mvn test
```

Covers the controller, service, and utility layers (`src/test/java`).

### Live testing without an API key

A `local` Spring profile is provided so you can exercise the full HTTP flow — validation, controller, service, circuit breaker — without any provider API key and without incurring any cost. It swaps in a stubbed [`StubChatProvider`](src/main/java/com/mel/cb/provider/StubChatProvider.java) that returns a canned reply instead of calling a real provider.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

By default this runs on port `8090` (configurable via `server.port` in [application-local.yml](src/main/resources/application-local.yml), useful if `8080` is already taken locally). No Postgres or Redis needed either — the `local` profile disables their autoconfiguration too.

Then either open the chat widget at `http://localhost:8090/bot/` and type a message, or use Swagger UI to try the endpoint directly:

`http://localhost:8090/bot/swagger-ui.html`

![Swagger UI overview](docs/screenshots/swagger-overview.png)

Expand `POST /chat/reply`, click **Try it out**, submit a payload, and hit **Execute**:

```json
{
  "botId": "support-bot",
  "userId": "user-123",
  "message": "Hello, what are your hours?"
}
```

![Executed request against the stubbed /chat/reply endpoint, returning a 200 with a canned reply](docs/screenshots/swagger-chat-reply-example.png)

### Live testing with real replies

Run without the `local` profile (i.e. `mvn spring-boot:run`) with at least one provider key set, then hit the endpoint directly:

```bash
curl -X POST http://localhost:8080/bot/chat/reply \
  -H "Content-Type: application/json" \
  -d '{"botId": "support-bot", "userId": "user-123", "message": "Hello, what are your hours?"}'
```

or use Swagger UI at `http://localhost:8080/bot/swagger-ui.html` the same way as above.

## API

### `POST /chat/reply`

Receives a bot identifier and the user's message, sends the message to whichever configured LLM provider is next in priority (via Spring AI), along with a configurable system prompt (`chatbot.system-prompt`), and returns the model's generated reply.

**Edge cases handled:**

- If the call to the LLM provider takes longer than 20 seconds, or every provider fails, a Resilience4j circuit breaker trips and a default reply is returned instead of hanging the request.
- If the input payload has a missing bot identifier or message, a 400 client error is thrown from the endpoint.
- All other exceptions are wrapped into user-friendly exceptions with appropriate messages.

**Response model (`ChatReply`):**

- `reply` — the chat reply text. Falls back to a default message if the AI call fails or the circuit breaker trips.
- `timestamp` — ISO 8601 format with fractional seconds, human-readable and chronologically sortable.
- `conversationId` — identifies the conversation for follow-up turns. Echoed back if the request included one, otherwise a new id is minted and returned here — pass it back on the next request to continue the same conversation (see [Conversation memory](#conversation-memory)).

## Conversation memory

Each conversation's recent turns and a rolling summary of older ones are kept in Redis for the length of a session (~8 hours of activity); the model sees them as real context on every reply, not just the latest message. Token usage is budgeted per user, split evenly across however many conversations that user currently has active — once a conversation nears its share, its oldest turns are summarised away to make room, and any durable facts spotted about the user (preferences, name, etc.) are saved to Postgres so they persist across sessions.

## Tools

The model can call a few tools mid-conversation when it decides one would help answer the user's question:

| Tool | Needs a key? | Powered by |
|---|---|---|
| Current weather | No | [Open-Meteo](https://open-meteo.com) |
| Current time in a place | No | Open-Meteo (geocoding) |
| Travel directions (driving/cycling/walking — not live transit schedules) | Yes — `OPENROUTESERVICE_API_KEY` | [OpenRouteService](https://openrouteservice.org) free tier |
| Web search | Yes — `TAVILY_API_KEY` | [Tavily](https://tavily.com) free tier |

Weather and time work out of the box. Directions and web search are off until their key is set — see [.env.example](.env.example) for where to get a free one.

## Further improvements

- Application monitoring for response time and performance across all endpoints.
- Richer structured logging (currently logs a simple per-request response time).
