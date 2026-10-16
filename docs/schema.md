# Lineage Graph Schema

Two kinds of graphs in FalkorDB:

- **Project graph** (one per repo, e.g. `lineage_<repo>`): shared history of the project.
- **Contributor graph** (one per user, e.g. `contrib_<login>`): private memory per contributor.

---

/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

## 1. Project graph

### Nodes

| Node       | Properties                                                         |
| ---------- | ------------------------------------------------------------------ |
| `Person`   | `login`, `name`                                                    |
| `Issue`    | `number`, `title`, `body`, `state`, `createdAt`, `url`             |
| `PR`       | `number`, `title`, `body`, `state`, `createdAt`, `mergedAt`, `url` |
| `File`     | `path`                                                             |
| `Topic`    | `name` (e.g. "auth", "caching", "ui")                              |
| `Decision` | `id`, `summary`, `date`, `sourceUrl`                               |
| `Handoff`  | `id`, `fromAgent`, `toAgent`, `payload`, `createdAt`               |

### Edges

```
(Person)-[:AUTHORED]->(Issue | PR)
(Person)-[:REVIEWED]->(PR)
(Person)-[:COMMENTED_ON]->(Issue | PR)
(PR)-[:CHANGED]->(File)
(PR)-[:FIXES]->(Issue)
(Issue | PR)-[:ABOUT]->(Topic)
(File)-[:ABOUT]->(Topic)
(Decision)-[:MADE_IN]->(Issue | PR)
(Decision)-[:AFFECTS]->(File)
(Decision)-[:ABOUT]->(Topic)
(Handoff)-[:REGARDING]->(PR | Issue)
(Handoff)-[:NEXT]->(Handoff)
```

---

## 2. Contributor graph (per user)

### Nodes

| Node                     | Properties                                                       |
| ------------------------ | ---------------------------------------------------------------- |
| `Contributor`            | `login`                                                          |
| `File`, `Topic`, `Issue` | same identifiers as the project graph (`path`, `name`, `number`) |

### Edges

```
(Contributor)-[:WORKED_ON {count, lastAt}]->(File)
(Contributor)-[:KNOWS {score}]->(Topic)
(Contributor)-[:ATTEMPTED {outcome}]->(Issue)
```

---

## 3. Questions the graph must answer

| #   | Question                                    | Path through the graph                                                               |
| --- | ------------------------------------------- | ------------------------------------------------------------------------------------ |
| 1   | Who should review this PR?                  | new PR → `CHANGED` → File ← `CHANGED` ← past PR ← `REVIEWED` ← Person; rank by count |
| 2   | Why was X decided?                          | Topic ← `ABOUT` ← Decision → `MADE_IN` → Issue/PR ← `COMMENTED_ON` ← Person          |
| 3   | What should this newcomer take?             | Contributor → `KNOWS` → Topic ← `ABOUT` ← open Issue (state = open)                  |
| 4   | Does this PR conflict with a past decision? | new PR → `CHANGED` → File ← `AFFECTS` ← Decision                                     |
| 5   | What did each agent do?                     | Handoff chain via `NEXT`, each linked by `REGARDING`                                 |

---

## 4. Agent flow on a new PR

1. **Triager** reads the PR, finds changed files and topics, writes a `Handoff` node.
2. **Matcher** reads that handoff, suggests reviewers (Q1), writes the next `Handoff`.
3. **Historian** checks for decision conflicts (Q4) and drafts the review comment.

---

## 5. Indexes (create early)

```
Person(login)
Issue(number)
PR(number)
File(path)
Topic(name)
Decision(id)
```

## 6. Notes

- `Decision` nodes are created by an LLM pass over issue/PR threads. Start with ~10-20 decisions; quality beats quantity.
- `Topic` nodes can be seeded from GitHub labels, then extended by the LLM.
- Keep property names exactly as above so Cypher in `db/Queries.java` stays consistent.
