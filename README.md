# Lineage

Lineage is a graph-backed GitHub repository intelligence application for FalkorDB. It ingests GitHub issues, pull requests, reviews, comments, changed files, topics, decisions, and agent handoffs into a project graph.

## Architecture

- **Backend:** Java 17, Spring Boot 3, Maven, Docker
- **Graph:** FalkorDB Cloud or local FalkorDB Docker
- **Frontend:** React, Vite, and `react-force-graph-2d`
- **Deployment:** Backend on Render, frontend on Vercel, database on FalkorDB Cloud

The canonical node, edge, and property names are documented in [docs/schema.md](docs/schema.md).

## Deployment

### Environment variables

| Variable | Backend/frontend | Default | Description |
| --- | --- | --- | --- |
| `FALKORDB_HOST` | Backend | `localhost` | FalkorDB hostname |
| `FALKORDB_PORT` | Backend | `6379` | FalkorDB Redis port |
| `FALKORDB_USER` | Backend | empty | FalkorDB Cloud username |
| `FALKORDB_PASSWORD` | Backend | empty | FalkorDB Cloud password |
| `GITHUB_TOKEN` | Backend | empty | Optional GitHub token |
| `LINEAGE_REPOSITORY` | Backend | empty | Optional startup repository, for example `owner/repository` |
| `LLM_API_KEY` | Backend | empty | Optional decision/agent model key |
| `ALLOWED_ORIGINS` | Backend | `http://localhost:5173` | Comma-separated browser origins |
| `VITE_API_URL` | Frontend | `http://localhost:8080` | Public backend URL |

Do not commit `.env` files, tokens, passwords, or deployment credentials.

### FalkorDB Cloud

Create a free FalkorDB Cloud database and copy its host, port, username, and password into the Render backend service. Keep the project graph name at its default unless you need a separate graph. FalkorDB Cloud free-tier storage may be ephemeral, so the backend automatically seeds demo data when the project graph is empty.

If both `LINEAGE_REPOSITORY` and `GITHUB_TOKEN` are configured, startup first attempts to ingest that repository. If ingestion fails, it loads the built-in demo seed instead.

### Render backend

1. Create a new Render **Web Service** from this repository.
2. Choose **Docker** as the runtime.
3. Set the Dockerfile path to `backend/Dockerfile`.
4. Set the Docker build context/root directory to `backend`.
5. Add the backend environment variables from the table above.
6. Set `ALLOWED_ORIGINS` to the final Vercel URL, for example `https://lineage.vercel.app`.
7. Deploy and verify:

   ```text
   https://<render-backend-domain>/health
   ```

   Expected response:

   ```json
   {"status":"ok"}
   ```

Render provides `PORT`; Spring Boot uses it automatically.

### Vercel frontend

1. Import this repository into Vercel.
2. Set the project root directory to `frontend`.
3. Use the Vite defaults:
   - Build command: `npm run build`
   - Output directory: `dist`
4. Set `VITE_API_URL` to the public Render backend URL.
5. Deploy the frontend.
6. Update Render’s `ALLOWED_ORIGINS` with the final Vercel domain and redeploy the backend if needed.

Vercel automatically serves the SPA entry point for this client-side application; no `vercel.json` is required.

## Local run

1. Copy the environment template:

   ```sh
   cp .env.example .env
   cp frontend/.env.example frontend/.env
   ```

2. Start local FalkorDB:

   ```sh
   docker compose up -d
   ```

3. Start the backend:

   ```sh
   cd backend
   mvn spring-boot:run
   ```

4. Start the frontend in another terminal:

   ```sh
   cd frontend
   npm install
   npm run dev
   ```

5. Open `http://localhost:5173`. With no environment variables set, the backend connects to local FalkorDB and seeds demo data when the graph is empty.

## API

- `GET /health` returns `{"status":"ok"}`.
- `GET /graph` returns graph nodes and links.
- `POST /ask` accepts `{ "question": "..." }`.
- `POST /pr` accepts `{ "prNumber": 2 }`.
- `GET /recommend/{login}` recommends open issues.
- `POST /ask/baseline` runs the graph-free baseline.
- `POST /simulate/{nodeId}` traverses downstream graph relationships.
- `POST /ingest` accepts `{ "repository": "owner/repository" }`.
