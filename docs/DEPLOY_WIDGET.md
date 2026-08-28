# Deploying the widget standalone (Vercel)

The chat widget ([`src/main/resources/static/index.html`](../src/main/resources/static/index.html))
is a single self-contained HTML file with no build step, so it can be deployed as its own static
site, separate from the Spring Boot backend, without duplicating the file anywhere.

These steps prepare and document the deployment; they haven't been executed against a real Vercel
account from this repo (see `docs/PLAN.md` Phase 4 notes).

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

Once deployed, the widget is on a different origin than the API (e.g.
`https://your-widget.vercel.app` vs `https://your-backend.onrender.com`), so it needs to know where
to send requests. Add one line before the widget's own `<script>` block in `index.html`:

```html
<script>window.CHAT_API_BASE_URL = "https://your-backend.onrender.com/bot";</script>
```

(include the `/bot` context path — see `application.yml`'s `server.servlet.context-path`). Without
this override the widget defaults to same-origin relative requests, which is correct when it's
served by the Spring Boot app itself but wrong once it's on Vercel.

## 3. Allow the widget's origin on the backend

The backend only accepts cross-origin `/chat/**` requests from origins listed in
`chatbot.cors.allowed-origins` (`com.mel.cb.config.CorsConfig`, Phase 4). Set
`CHATBOT_CORS_ALLOWED_ORIGINS` on the backend host to include the deployed widget's origin,
comma-separated with any others you still need (e.g. local dev servers):

```
CHATBOT_CORS_ALLOWED_ORIGINS=https://your-widget.vercel.app
```

## 4. Redeploy

Push to the branch Vercel is tracking (or `vercel --prod`) to deploy the widget; restart/redeploy
the backend after changing `CHATBOT_CORS_ALLOWED_ORIGINS` for the new origin to take effect.
