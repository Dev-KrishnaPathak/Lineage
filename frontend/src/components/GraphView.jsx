import { useEffect, useMemo, useRef, useState } from 'react'
import ForceGraph2D from 'react-force-graph-2d'
import { simulate } from '../api'

const COLORS = {
  Person: '#64e5c1',
  PR: '#72a9ff',
  Issue: '#ffb86b',
  File: '#c79bff',
  Decision: '#ff8d9d',
  Topic: '#8bd5ff',
  Handoff: '#f6d365',
}

function GraphView({ graph, repository = 'No repository loaded', pathNodeIds = [], onPathChange, onNodeSelect }) {
  const graphRef = useRef()
  const containerRef = useRef()
  const [size, setSize] = useState({ width: 0, height: 640 })
  const [selectedNode, setSelectedNode] = useState(null)
  const [hoveredNode, setHoveredNode] = useState(null)
  const [error, setError] = useState('')
  const highlighted = useMemo(() => new Set(pathNodeIds), [pathNodeIds])
  const graphData = useMemo(() => ({
    nodes: graph.nodes || [],
    links: (graph.links || []).map((link) => ({
      ...link,
      source: typeof link.source === 'object' ? link.source.id : link.source,
      target: typeof link.target === 'object' ? link.target.id : link.target,
    })),
  }), [graph])
  const nodesById = useMemo(
    () => new Map(graphData.nodes.map((node) => [node.id, node])),
    [graphData.nodes],
  )

  useEffect(() => {
    const container = containerRef.current
    if (!container) return undefined
    const updateSize = () => {
      const bounds = container.getBoundingClientRect()
      setSize({ width: Math.max(320, Math.floor(bounds.width)), height: Math.max(440, Math.floor(bounds.height)) })
    }
    const observer = new ResizeObserver(updateSize)
    observer.observe(container)
    updateSize()
    return () => observer.disconnect()
  }, [])

  useEffect(() => {
    if (graphData.nodes.length > 0) {
      const timer = window.setTimeout(() => graphRef.current?.zoomToFit(500, 60), 120)
      return () => window.clearTimeout(timer)
    }
    return undefined
  }, [graphData])

  const clickNode = async (node) => {
    setSelectedNode(node)
    onNodeSelect(node)
    setError('')
    try {
      const result = await simulate(node.id)
      onPathChange(result.affectedNodeIds || [])
    } catch (requestError) {
      setError(requestError.message)
    }
  }

  const hasFocus = highlighted.size > 0
  const isFocused = (node) => highlighted.has(node.id) || hoveredNode?.id === node.id || selectedNode?.id === node.id
  const linkNode = (value) => typeof value === 'object' ? value : nodesById.get(value)
  const linkLabel = (link) => {
    if (link.type !== 'REVIEWED') return link.type
    const reviewer = linkNode(link.source)?.label || 'Unknown reviewer'
    const pullRequest = linkNode(link.target)?.label || 'Unknown pull request'
    return `${reviewer} reviewed PR #${pullRequest}`
  }
  const shortLabel = (node) => {
    if (node.type === 'PR' || node.type === 'Issue') return `#${node.label}`
    if (node.type === 'File') return String(node.label).split('/').pop()
    return String(node.label)
  }
  const nodeColor = (node) => {
    if (highlighted.has(node.id)) return '#ffffff'
    if (hasFocus && !isFocused(node)) return '#294257'
    return COLORS[node.type] || '#94a3b8'
  }
  const linkIsHighlighted = (link) => highlighted.has(link.source.id || link.source) && highlighted.has(link.target.id || link.target)

  return (
    <section className="graph-card" ref={containerRef}>
      <div className="section-heading">
        <span className="eyebrow">PROJECT GRAPH</span>
        <h2>{repository === 'No repository loaded' ? 'Repository graph' : `${repository} graph`}</h2>
      </div>
      <div className="graph-legend">
        {['Person', 'PR', 'Issue', 'File', 'Decision', 'Topic'].map((type) => (
          <span key={type}><i className={`legend-dot legend-${type.toLowerCase()}`} />{type}</span>
        ))}
        <span><i className="legend-line legend-reviewed" />Reviewer → PR</span>
      </div>
      {graphData.nodes.length === 0 && <div className="graph-empty"><strong>No graph data yet</strong><span>Load a GitHub repository above to build its lineage map.</span></div>}
      {error && <div className="graph-error" role="alert">{error}</div>}
      {size.width > 0 && graphData.nodes.length > 0 && <ForceGraph2D
        ref={graphRef}
        graphData={graphData}
        nodeLabel={(node) => `${node.type}: ${node.title || node.label}`}
        nodeColor={nodeColor}
        nodeVal={(node) => highlighted.has(node.id) ? 9 : isFocused(node) ? 7 : 4}
        nodeCanvasObject={(node, ctx, globalScale) => {
          const color = nodeColor(node)
          const focused = isFocused(node)
          const radius = highlighted.has(node.id) ? 7 : focused ? 6 : 4
          ctx.beginPath()
          ctx.arc(node.x, node.y, radius, 0, 2 * Math.PI)
          ctx.fillStyle = color
          ctx.shadowColor = color
          ctx.shadowBlur = highlighted.has(node.id) ? 18 : focused ? 12 : 2
          ctx.fill()
          ctx.shadowBlur = 0
          const showLabel = hoveredNode?.id === node.id || selectedNode?.id === node.id
          if (showLabel) {
            ctx.font = `500 ${Math.max(10, 12 / globalScale)}px Inter, sans-serif`
            ctx.textAlign = 'center'
            ctx.textBaseline = 'top'
            const label = shortLabel(node)
            const displayLabel = label.length > 34 ? `${label.slice(0, 31)}…` : label
            const labelWidth = ctx.measureText(displayLabel).width
            const labelX = node.x - labelWidth / 2 - 6
            const labelY = node.y + radius + 3
            ctx.fillStyle = 'rgba(5, 6, 8, .94)'
            ctx.fillRect(labelX, labelY - 2, labelWidth + 12, 18)
            ctx.strokeStyle = 'rgba(100, 229, 193, .28)'
            ctx.lineWidth = 1
            ctx.strokeRect(labelX, labelY - 2, labelWidth + 12, 18)
            ctx.fillStyle = '#f1f5f9'
            ctx.fillText(displayLabel, node.x, labelY + 1)
          }
        }}
        linkLabel={linkLabel}
        linkColor={(link) => linkIsHighlighted(link)
          ? '#ffffff'
          : link.type === 'REVIEWED'
            ? '#64e5c1'
            : hasFocus ? '#172c3e' : '#38536c'}
        linkWidth={(link) => linkIsHighlighted(link) ? 2.5 : link.type === 'REVIEWED' ? 2 : 1}
        linkDirectionalArrowLength={4}
        linkDirectionalArrowRelPos={1}
        linkDirectionalParticles={(link) => linkIsHighlighted(link) ? 2 : 0}
        linkDirectionalParticleWidth={2}
        onNodeClick={clickNode}
        onNodeHover={setHoveredNode}
        onBackgroundClick={() => { setSelectedNode(null); onNodeSelect(null) }}
        cooldownTicks={100}
        d3AlphaDecay={0.035}
        d3VelocityDecay={0.35}
        backgroundColor="#08090b"
        width={size.width}
        height={size.height}
      />}
    </section>
  )
}

export default GraphView
