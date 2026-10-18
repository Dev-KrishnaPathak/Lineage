package com.lineage.db;

import java.util.List;

public final class Queries {

    private Queries() {
    }

    public static final List<String> PROJECT_INDEXES = List.of(
            "CREATE INDEX FOR (n:Person) ON (n.login)",
            "CREATE INDEX FOR (n:Issue) ON (n.number)",
            "CREATE INDEX FOR (n:PR) ON (n.number)",
            "CREATE INDEX FOR (n:File) ON (n.path)",
            "CREATE INDEX FOR (n:Topic) ON (n.name)",
            "CREATE INDEX FOR (n:Decision) ON (n.id)"
    );

    public static final String MERGE_PERSON = """
            MERGE (p:Person {login: $login})
            SET p.name = $name
            """;
    public static final String MERGE_ISSUE = """
            MERGE (i:Issue {number: $number})
            SET i.title = $title, i.body = $body, i.state = $state,
                i.createdAt = $createdAt, i.url = $url
            """;
    public static final String MERGE_PR = """
            MERGE (p:PR {number: $number})
            SET p.title = $title, p.body = $body, p.state = $state,
                p.createdAt = $createdAt, p.mergedAt = $mergedAt, p.url = $url
            """;
    public static final String MERGE_FILE = "MERGE (f:File {path: $path})";
    public static final String MERGE_TOPIC = "MERGE (t:Topic {name: $name})";
    public static final String LINK_AUTHORED = """
            MATCH (p:Person {login: $login}), (target {number: $number})
            MERGE (p)-[:AUTHORED]->(target)
            """;
    public static final String LINK_REVIEWED = """
            MATCH (p:Person {login: $login}), (pr:PR {number: $number})
            MERGE (p)-[:REVIEWED]->(pr)
            """;
    public static final String LINK_COMMENTED = """
            MATCH (p:Person {login: $login}), (target {number: $number})
            MERGE (p)-[:COMMENTED_ON]->(target)
            """;
    public static final String LINK_CHANGED = """
            MATCH (pr:PR {number: $number}), (f:File {path: $path})
            MERGE (pr)-[:CHANGED]->(f)
            """;
    public static final String LINK_ABOUT = """
            MATCH (target {number: $number}), (t:Topic {name: $name})
            MERGE (target)-[:ABOUT]->(t)
            """;
    public static final String MERGE_DECISION = """
            MERGE (d:Decision {id: $id})
            SET d.summary = $summary, d.date = $date, d.sourceUrl = $sourceUrl
            """;
    public static final String LINK_DECISION_MADE_IN = """
            MATCH (d:Decision {id: $id}), (target {number: $number})
            MERGE (d)-[:MADE_IN]->(target)
            """;
    public static final String LINK_DECISION_ABOUT = """
            MATCH (d:Decision {id: $id}), (t:Topic {name: $name})
            MERGE (d)-[:ABOUT]->(t)
            """;
    public static final String LINK_DECISION_AFFECTS = """
            MATCH (d:Decision {id: $id}), (f:File {path: $path})
            MERGE (d)-[:AFFECTS]->(f)
            """;
    public static final String SUGGEST_REVIEWERS = """
            MATCH (new:PR {number: $number})-[:CHANGED]->(f:File)
                  <-[:CHANGED]-(past:PR)<-[:REVIEWED]-(person:Person)
            RETURN person.login AS login, count(DISTINCT past) AS score,
                   id(new) AS newId, id(f) AS fileId, id(past) AS pastId, id(person) AS personId
            ORDER BY score DESC
            """;
    public static final String FIND_REVIEWERS = """
            MATCH (person:Person)-[:REVIEWED]->(pr:PR {number: $number})
            RETURN person.login AS login, id(pr) AS prId, id(person) AS personId
            ORDER BY person.login
            """;
    public static final String FIND_PR = """
            MATCH (pr:PR {number: $number})
            RETURN pr.number AS number, pr.title AS title, id(pr) AS prId
            """;
    public static final String FIND_OWNERS = """
            MATCH (pr:PR)-[:CHANGED]->(f:File {path: $path}),
                  (person:Person)-[:REVIEWED]->(pr)
            RETURN person.login AS login, count(DISTINCT pr) AS score,
                   id(f) AS fileId, id(pr) AS prId, id(person) AS personId
            ORDER BY score DESC
            """;
    public static final String TRACE_DECISION = """
            MATCH (topic:Topic {name: $topic})<-[:ABOUT]-(decision:Decision)
                  -[:MADE_IN]->(item)<-[:COMMENTED_ON]-(person:Person)
            RETURN decision.id AS decisionId, decision.summary AS summary,
                   person.login AS login, id(topic) AS topicId, id(decision) AS decisionNodeId,
                   id(item) AS itemId, id(person) AS personId
            """;
    public static final String FIND_CONFLICTS = """
            MATCH (pr:PR {number: $number})-[:CHANGED]->(file:File)
                  <-[:AFFECTS]-(decision:Decision)
            RETURN decision.id AS decisionId, decision.summary AS summary,
                   file.path AS path, id(pr) AS prId, id(file) AS fileId, id(decision) AS decisionNodeId
            """;
    public static final String RECOMMEND_ISSUES = """
            MATCH (person:Person {login: $login})-[:REVIEWED|AUTHORED]->(past)
                  -[:ABOUT]->(topic:Topic)<-[:ABOUT]-(issue:Issue {state: 'open'})
            RETURN issue.number AS number, issue.title AS title,
                   id(person) AS personId, id(past) AS pastId, id(topic) AS topicId, id(issue) AS issueId
            """;
    public static final String MERGE_CONTRIBUTOR = "MERGE (c:Contributor {login: $login})";
    public static final String MERGE_CONTRIBUTOR_FILE = "MERGE (f:File {path: $path})";
    public static final String MERGE_CONTRIBUTOR_TOPIC = "MERGE (t:Topic {name: $name})";
    public static final String MERGE_CONTRIBUTOR_ISSUE = "MERGE (i:Issue {number: $number})";
    public static final String LINK_WORKED_ON = """
            MATCH (c:Contributor {login: $login}), (f:File {path: $path})
            MERGE (c)-[r:WORKED_ON]->(f)
            SET r.count = coalesce(r.count, 0) + 1, r.lastAt = $lastAt
            """;
    public static final String LINK_KNOWS = """
            MATCH (c:Contributor {login: $login}), (t:Topic {name: $name})
            MERGE (c)-[r:KNOWS]->(t)
            SET r.score = coalesce(r.score, 0) + $score
            """;
    public static final String LINK_ATTEMPTED = """
            MATCH (c:Contributor {login: $login}), (i:Issue {number: $number})
            MERGE (c)-[r:ATTEMPTED]->(i)
            SET r.outcome = $outcome
            """;
    public static final String MERGE_HANDOFF = """
            MERGE (h:Handoff {id: $id})
            SET h.fromAgent = $fromAgent, h.toAgent = $toAgent,
                h.payload = $payload, h.createdAt = $createdAt
            """;
    public static final String LINK_HANDOFF_NEXT = """
            MATCH (previous:Handoff {id: $previousId}), (next:Handoff {id: $nextId})
            MERGE (previous)-[:NEXT]->(next)
            """;
    public static final String LINK_HANDOFF_REGARDING = """
            MATCH (h:Handoff {id: $handoffId}), (target {number: $number})
            MERGE (h)-[:REGARDING]->(target)
            """;
    public static final String GRAPH_NODES = """
            MATCH (n)
            RETURN id(n) AS id, labels(n) AS labels,
                   coalesce(n.login, n.name, n.path, n.number, n.id) AS label
            """;
    public static final String GRAPH_NODE_COUNT = "MATCH (n) RETURN count(n) AS count";
    public static final String GRAPH_LINKS = """
            MATCH (source)-[r]->(target)
            RETURN id(source) AS source, id(target) AS target, type(r) AS type
            """;
    public static final String SIMULATE_DOWNSTREAM = """
            MATCH (start)-[*1..]->(node)
            WHERE id(start) = $nodeId
            RETURN DISTINCT id(node) AS id
            """;
    public static final String NODE_DETAILS = """
            MATCH (n)
            WHERE id(n) = $nodeId
            RETURN labels(n) AS labels, n.login AS login, n.name AS name,
                   n.number AS number, n.title AS title, n.body AS body,
                   n.state AS state, n.path AS path, n.url AS url,
                   n.summary AS summary, n.sourceUrl AS sourceUrl,
                   n.fromAgent AS fromAgent, n.toAgent AS toAgent,
                   n.payload AS payload
            """;
}
