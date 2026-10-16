# Lineage

Lineage is a graph-backed GitHub repository intelligence demo for FalkorDB Graph Hacks. It ingests GitHub issues, pull requests, reviews, comments, changed files, topics, decisions, and agent handoffs into a FalkorDB project graph. The UI shows the graph path behind answers and lets users simulate downstream impact.

## Architecture

- **Backend:** Java 17, Spring Boot 3, JFalkorDB, Jackson, LangChain4j
- **Graph:** FalkorDB, with one `lineage_<repo>` project graph and `contrib_<login>` contributor graphs
- **Ingestion:** GitHub REST API with pagination and a demo-data fallback
- **Agents:** Triager -> Matcher -> Historian, with chained `Handoff` nodes
- **Frontend:** React, Vite, and `react-force-graph-2d`
- **Baseline:** isolated embedding similarity search with no graph queries

The canonical node, edge, and property names are documented in [docs/schema.md](docs/schema.md).

## Setup

1. Copy the environment template:

   ```sh
   cp .env.example .env
   ```

2. Set `GITHUB_REPOSITORY` to `owner/repository`. Add `GITHUB_TOKEN` and `LLM_API_KEY` when available.

3. Start FalkorDB:

   ```sh
   docker compose up -d
   ```

4. Install frontend dependencies:

   ```sh
   cd frontend
   npm install
   ```

## Run

Start the backend from the repository root:

```sh
cd backend
mvn spring-boot:run
```

Enable ingestion for a run:

```sh
INGEST_ENABLED=true mvn spring-boot:run
```

Start the frontend in another terminal:

```sh
cd frontend
npm run dev
```

The frontend runs at http://localhost:5173 and the backend at http://localhost:8080.

## API

- `GET /graph` returns graph nodes and links.
- `POST /ask` accepts `{ "question": "..." }` and returns an answer, sources, and `pathNodeIds`.
- `POST /pr` accepts `{ "prNumber": 2 }` and returns reviewers, conflicts, a draft comment, handoffs, and path IDs.
- `GET /recommend/{login}` recommends open issues.
- `POST /ask/baseline` runs the graph-free baseline.
- `POST /simulate/{nodeId}` traverses downstream graph relationships.

## Demo script

1. Start FalkorDB and run the backend with `INGEST_ENABLED=true`. If GitHub configuration is unavailable, the demo dataset is loaded automatically.
2. Open the frontend and select **Ask the graph**.
3. Ask why caching was decided or ask what a newcomer should work on.
4. Click **Review** for PR `2` to see the reviewer/conflict tools and the Triager -> Matcher -> Historian timeline.
5. Click a highlighted graph node to run the downstream what-if simulation.
6. Use **RAG vs graph** to compare the graph-backed response with the isolated baseline.