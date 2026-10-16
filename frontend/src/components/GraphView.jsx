import { useEffect, useRef } from 'react'
import ForceGraph2D from 'react-force-graph-2d'
import { simulate } from '../api'

function GraphView({ graph, pathNodeIds = [], onPathChange }) {
  const graphRef = useRef()
  const highlighted = new Set(pathNodeIds)

  useEffect(() => {
    graphRef.current?.zoomToFit(400, 30)
  }, [graph])

  const clickNode = async (node) => {
    try {
      const result = await simulate(node.id)
      onPathChange(result.affectedNodeIds || [])
    } catch (error) {
      console.error(error)
    }
  }

  return (
    <section className="graph-card">
      <div className="section-heading">
        <span className="eyebrow">PROJECT GRAPH</span>
        <h2>Lineage map</h2>
      </div>
      <ForceGraph2D
        ref={graphRef}
        graphData={graph}
        nodeLabel={(node) => `${node.type}: ${node.label}`}
        nodeColor={(node) => highlighted.has(node.id) ? '#f59e0b' : '#5eead4'}
        nodeVal={(node) => highlighted.has(node.id) ? 8 : 5}
        linkLabel="type"
        linkColor={(link) => highlighted.has(link.source.id || link.source) && highlighted.has(link.target.id || link.target) ? '#f59e0b' : '#475569'}
        onNodeClick={clickNode}
        backgroundColor="#08111f"
        width={window.innerWidth * 0.55}
        height={window.innerHeight - 80}
      />
    </section>
  )
}

export default GraphView
