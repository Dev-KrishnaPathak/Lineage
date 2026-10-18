function AgentTimeline({ handoffs = [] }) {
  return <section className="timeline-section"><div className="section-title"><span className="eyebrow">Agent trail</span><h2>Review handoffs</h2></div>{handoffs.length === 0 ? <p className="muted">Run a PR review to see the three-step agent trail.</p> : <ol className="timeline">{handoffs.map((handoff, index) => <li key={handoff.id}><span className="timeline-number">{index + 1}</span><div><strong>{handoff.fromAgent} <span>→</span> {handoff.toAgent}</strong><p>{handoff.payload}</p></div></li>)}</ol>}</section>
}

export default AgentTimeline
