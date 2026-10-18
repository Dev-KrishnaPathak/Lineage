function NodeDetails({ node, details, graph, loading, error, onClose, onNodeSelect }) {
  if (!node) return null

  const connections = (graph.links || []).flatMap((link) => {
    const source = link.source?.id ?? link.source
    const target = link.target?.id ?? link.target
    if (source !== node.id && target !== node.id) return []
    const otherId = source === node.id ? target : source
    const other = (graph.nodes || []).find((item) => item.id === otherId)
    return other ? [{ ...other, relation: source === node.id ? link.type : `${link.type} from` }] : []
  })
  const title = details?.title || details?.name || details?.path || node.label
  const date = details?.createdAt || details?.date || details?.mergedAt

  return <aside className="node-drawer" aria-label="Node details">
    <div className="drawer-header"><div><span className="eyebrow">{details?.type || node.type}</span><h2>{title}</h2></div><button className="close-button" onClick={onClose} aria-label="Close details">×</button></div>
    {loading && <p className="muted">Loading repository details…</p>}
    {error && <p className="error">{error}</p>}
    {!loading && !error && <div className="drawer-body">
      {details?.state && <Detail label="State" value={details.state} />}
      {details?.login && <Detail label="Person" value={details.login} />}
      {details?.path && <Detail label="File" value={details.path} />}
      {date && <Detail label="Date" value={date} />}
      {details?.body && <div className="drawer-copy"><span>Description</span><p>{details.body}</p></div>}
      {(details?.url || details?.sourceUrl) && <a className="github-link" href={details.url || details.sourceUrl} target="_blank" rel="noreferrer">Open on GitHub ↗</a>}
      <div className="connection-list"><span className="result-label">Connections</span>{connections.length ? connections.map((connection) => <button className="connection" key={`${connection.relation}-${connection.id}`} onClick={() => onNodeSelect(connection)}><span>{connection.relation}</span><strong>{connection.type === 'PR' || connection.type === 'Issue' ? `#${connection.label}` : connection.label}</strong></button>) : <p className="muted">No connected nodes.</p>}</div>
    </div>}
  </aside>
}

function Detail({ label, value }) {
  return <div className="detail-row"><span>{label}</span><strong>{value}</strong></div>
}

export default NodeDetails
