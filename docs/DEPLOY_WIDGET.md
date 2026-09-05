# Deploying the widget standalone (Vercel)

The chat widget ([`src/main/resources/static/index.html`](../src/main/resources/static/index.html))
is a single self-contained HTML file with no build step, so it can be deployed as its own static
site, separate from the Spring Boot backend, without duplicating the file anywhere.

Live at **https://chat-bot-service-rust.vercel.app**, talking cross-origin to the backend above with
CORS and API-key auth both configured.

## 1. Point Vercel at the widget directory

The [`vercel.json`](../vercel.json) at the repo root already sets:

```json
{ "outputDirectory": "src/main/resources/static" }
```

Import this repo into Vercel (dashboard → **Add New… → Project**, or `vercel` from the repo root
with the CLI). With `outputDirectory` set, Vercel serves that directory's contents as-is — no
framework preset, no build command needed. (Equivalent alternative: skip `vercel.json` and instead
set the project's **Root Directory** to `src/main/resources/static` in the dashboard.)

## 2. Point the widget at the deployed backend

`index.html` already defaults `API_BASE_URL` to the live Render backend
(`https://chat-bot-service-06gv.onrender.com/bot`) — an absolute URL works the same whether the
file is served same-origin by the Spring Boot app or standalone on Vercel, so no edit is needed for
that backend. Only override it (via a `<script>window.CHAT_API_BASE_URL = "...";</script>` tag
before the widget's own `<script>` block) if pointing this deployment at a different backend
instance.

## 3. Allow the widget's origin on the backend

The backend only accepts cross-origin `/chat/**` requests from origins listed in
`chatbot.cors.allowed-origins` (`com.mel.cb.config.CorsConfig`). Set
`CHATBOT_CORS_ALLOWED_ORIGINS` on the backend host to include the deployed widget's origin,
comma-separated with any others you still need (e.g. local dev servers):

```
CHATBOT_CORS_ALLOWED_ORIGINS=https://your-widget.vercel.app
```

## 4. Redeploy

Push to the branch Vercel is tracking (or `vercel --prod`) to deploy the widget; restart/redeploy
the backend after changing `CHATBOT_CORS_ALLOWED_ORIGINS` for the new origin to take effect.
