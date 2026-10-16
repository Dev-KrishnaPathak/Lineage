import { useEffect, useState } from 'react'
import './App.css'
import { getGraph, recommend } from './api'
import GraphView from './components/GraphView'
import AskPanel from './components/AskPanel'
import PRPanel from './components/PRPanel'
import AgentTimeline from './components/AgentTimeline'
import RagVsGraph from './components/RagVsGraph'

function App() {
  const [graph, setGraph] = useState({ nodes: [], links: [] })
  const [pathNodeIds, setPathNodeIds] = useState([])
  const [handoffs, setHandoffs] = useState([])
  const [login, setLogin] = useState('demo-user')
  const [issues, setIssues] = useState(null)

  useEffect(() => {
    getGraph().then(setGraph).catch(console.error)
  }, [])

  const loadRecommendations = async () => {
    const result = await recommend(login)
    setIssues(result.issues)
    setPathNodeIds(result.pathNodeIds || [])
  }

  return (
    <main className="app-shell">
      <header className="topbar"><div><span className="eyebrow">FALKORDB GRAPH HACKS</span><h1>Lineage</h1></div><span className="status-dot">LIVE GRAPH</span></header>
      <div className="workspace">
        <GraphView graph={graph} pathNodeIds={pathNodeIds} onPathChange={setPathNodeIds} />
        <aside className="sidebar">
          <AskPanel onPathChange={setPathNodeIds} />
          <PRPanel onPathChange={setPathNodeIds} onHandoffs={setHandoffs} />
          <AgentTimeline handoffs={handoffs} />
          <RagVsGraph onPathChange={setPathNodeIds} />
          <section className="panel"><div className="section-heading"><span className="eyebrow">CONTRIBUTOR</span><h2>Recommendations</h2></div><div className="form-row"><select value={login} onChange={(event) => setLogin(event.target.value)}><option>demo-user</option></select><button type="button" onClick={loadRecommendations}>Find issues</button></div>{issues && <pre>{JSON.stringify(issues, null, 2)}</pre>}</section>
        </aside>
      </div>
    </main>
  )
}

export default App
