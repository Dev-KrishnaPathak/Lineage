function AgentTimeline({ handoffs = [] }) {
  return (
    <section className="panel">
      <div className="section-heading"><span className="eyebrow">AGENT TRAIL</span><h2>Handoffs</h2></div>
      {handoffs.length === 0 ? <p className="muted">Run a PR review to see the agent trail.</p> : handoffs.map((handoff) => (
        <article className="timeline-item" key={handoff.id}>
          <strong>{handoff.fromAgent} → {handoff.toAgent}</strong>
          <p>{handoff.payload}</p>
        </article>
      ))}
    </section>
  )
}

export default AgentTimeline
