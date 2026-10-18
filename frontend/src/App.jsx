import { useEffect, useState } from 'react'
import './App.css'
import { getGraph, getIngestStatus, getNodeDetails, ingest, recommend } from './api'
import GraphView from './components/GraphView'
import AskPanel from './components/AskPanel'
import PRPanel from './components/PRPanel'
import AgentTimeline from './components/AgentTimeline'
import RagVsGraph from './components/RagVsGraph'
import NodeDetails from './components/NodeDetails'
import Spinner from './components/Spinner'

const TABS = [
  { id: 'ask', label: 'Ask', hint: 'Query connected evidence' },
  { id: 'review', label: 'Review PR', hint: 'Find reviewers and conflicts' },
  { id: 'compare', label: 'Compare', hint: 'Graph vs baseline' },
]

function App() {
  const [graph, setGraph] = useState({ nodes: [], links: [] })
  const [pathNodeIds, setPathNodeIds] = useState([])
  const [handoffs, setHandoffs] = useState([])
  const [login, setLogin] = useState('demo-user')
  const [issues, setIssues] = useState(null)
  const [activeTab, setActiveTab] = useState('ask')
  const [error, setError] = useState('')
  const [loadingRecommendations, setLoadingRecommendations] = useState(false)
  const [repository, setRepository] = useState('')
  const [loadingIngest, setLoadingIngest] = useState(false)
  const [ingestProgress, setIngestProgress] = useState({ progress: 0, message: '' })
  const [loadedRepository, setLoadedRepository] = useState('No repository loaded')
  const [selectedNode, setSelectedNode] = useState(null)
  const [nodeDetails, setNodeDetails] = useState(null)
  const [nodeDetailsLoading, setNodeDetailsLoading] = useState(false)
  const [nodeDetailsError, setNodeDetailsError] = useState('')
  const [backendStatus, setBackendStatus] = useState('checking')

  useEffect(() => {
    let cancelled = false
    const loadInitialGraph = async () => {
      for (let attempt = 0; attempt < 3; attempt++) {
        if (cancelled) return
        if (attempt > 0) {
          setBackendStatus('waking')
          await new Promise((resolve) => window.setTimeout(resolve, 3000))
        }
        try {
          const loadedGraph = await getGraph()
          if (!cancelled) {
            setGraph(loadedGraph)
            setBackendStatus('ready')
          }
          return
        } catch {
          if (!cancelled) setBackendStatus('waking')
        }
      }
      if (!cancelled) setBackendStatus('offline')
    }
    loadInitialGraph()
    return () => { cancelled = true }
  }, [])

  const selectNode = async (node) => {
    setSelectedNode(node)
    setNodeDetails(null)
    setNodeDetailsError('')
    if (!node) return
    setNodeDetailsLoading(true)
    try {
      setNodeDetails(await getNodeDetails(node.id))
    } catch {
      setNodeDetails({
        type: node.type,
        title: node.type === 'PR' || node.type === 'Issue' ? `#${node.label}` : node.label,
        path: node.type === 'File' ? node.label : '',
      })
      setNodeDetailsError('')
    } finally {
      setNodeDetailsLoading(false)
    }
  }

  const loadRecommendations = async () => {
    setLoadingRecommendations(true)
    try {
      const result = await recommend(login)
      setIssues(result.issues)
      setPathNodeIds(result.pathNodeIds || [])
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setLoadingRecommendations(false)
    }
  }

  const loadRepository = async (event) => {
    event.preventDefault()
    setError('')
    setLoadingIngest(true)
    try {
      let status = await ingest(repository.trim())
      setIngestProgress({ progress: status.progress, message: status.message })
      while (status.running) {
        await new Promise((resolve) => window.setTimeout(resolve, 700))
        status = await getIngestStatus()
        setIngestProgress({ progress: status.progress, message: status.message })
        if (status.error) throw new Error(status.error)
      }
      if (status.phase === 'failed') throw new Error(status.error || 'GitHub ingestion failed.')
      setGraph(await getGraph())
      setPathNodeIds([])
      setSelectedNode(null)
      setNodeDetails(null)
      setHandoffs([])
      setIssues(null)
      setLoadedRepository(status.repository)
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setLoadingIngest(false)
      setIngestProgress({ progress: 0, message: '' })
    }
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <div className="brand"><span className="brand-mark">L</span><div><p className="brand-name">Lineage</p><span className="brand-subtitle">Repository intelligence</span></div></div>
        <form className="repo-form" onSubmit={loadRepository}>
          <span className="repo-label">Analyze a repo</span>
          <input aria-label="GitHub repository" value={repository} onChange={(event) => setRepository(event.target.value)} placeholder="owner/repository" />
          <button type="submit" disabled={loadingIngest}>{loadingIngest ? <Spinner /> : 'Analyze'}</button>
        </form>
        <div className="topbar-stats">
          <span><strong>{graph.nodes.length}</strong> nodes</span>
          <span><strong>{graph.links.length}</strong> relationships</span>
        </div>
      </header>
      {loadingIngest && <div className="ingest-bar"><span>{ingestProgress.message || 'Preparing repository'}</span><strong>{ingestProgress.progress}%</strong><i style={{ width: `${Math.max(4, ingestProgress.progress)}%` }} /></div>}
      {backendStatus !== 'ready' && <div className="app-error" role="status">{backendStatus === 'offline' ? 'The backend is unavailable. Please try again in a moment.' : 'Waking up the server, retrying…'}</div>}
      {error && <div className="app-error" role="alert">{error}</div>}
      <div className="workspace">
        <GraphView graph={graph} repository={loadedRepository} pathNodeIds={pathNodeIds} onPathChange={setPathNodeIds} onNodeSelect={selectNode} />
        <section className="tool-shell">
          <nav className="tabs" aria-label="Repository tools">
            {TABS.map((tab) => <button key={tab.id} className={activeTab === tab.id ? 'tab active' : 'tab'} onClick={() => setActiveTab(tab.id)}><strong>{tab.label}</strong><span>{tab.hint}</span></button>)}
          </nav>
          <div className="tab-content">
            {activeTab === 'ask' && <><AskPanel onPathChange={setPathNodeIds} /><section className="secondary-section"><div className="section-title"><span className="eyebrow">Next steps</span><h2>Recommended issues</h2></div><div className="form-row"><select value={login} onChange={(event) => setLogin(event.target.value)}><option>demo-user</option><option>cache-maintainer</option></select><button type="button" onClick={loadRecommendations} disabled={loadingRecommendations}>{loadingRecommendations ? <Spinner /> : 'Find issues'}</button></div>{issues ? <div className="issue-list">{issues.length ? issues.map((issue) => <button className="issue-item" key={issue.number} onClick={() => setPathNodeIds(issue.pathNodeIds || [])}><strong>#{issue.number}</strong><span>{issue.title}</span></button>) : <EmptyState text="No connected open issues found." />}</div> : <p className="muted">Find open issues connected to a contributor’s history.</p>}</section></>}
            {activeTab === 'review' && <><PRPanel onPathChange={setPathNodeIds} onHandoffs={setHandoffs} /><AgentTimeline handoffs={handoffs} /></>}
            {activeTab === 'compare' && <RagVsGraph onPathChange={setPathNodeIds} />}
          </div>
        </section>
        <NodeDetails node={selectedNode} details={nodeDetails} graph={graph} loading={nodeDetailsLoading} error={nodeDetailsError} onClose={() => selectNode(null)} onNodeSelect={selectNode} />
      </div>
    </main>
  )
}

function EmptyState({ text }) {
  return <p className="empty-state">{text}</p>
}

export default App
