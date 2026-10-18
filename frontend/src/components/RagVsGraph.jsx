import { useState } from 'react'
import { ask, askBaseline } from '../api'
import Spinner from './Spinner'

function RagVsGraph({ onPathChange }) {
  const [question, setQuestion] = useState('Who should review a change to CacheService and why?')
  const [answers, setAnswers] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const submit = async (event) => {
    event.preventDefault()
    setError('')
    setLoading(true)
    try {
      const [graph, baseline] = await Promise.all([ask(question), askBaseline(question)])
      setAnswers({ graph, baseline })
      onPathChange(graph.pathNodeIds || [])
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setLoading(false)
    }
  }

  return <div className="tool-panel"><div className="panel-heading"><div><span className="eyebrow">Evidence comparison</span><h1>Compare answers</h1></div></div><p className="panel-description">See what connected repository context adds beyond document similarity.</p><form onSubmit={submit} className="form-row"><input value={question} onChange={(event) => setQuestion(event.target.value)} /><button type="submit" disabled={loading}>{loading ? <Spinner /> : 'Compare'}</button></form>{error && <p className="error" role="alert">{error}</p>}{answers && <div className="comparison"><AnswerCard title="Graph" text={answers.graph.answer} caption="Connected nodes, relationships, and decisions" /><AnswerCard title="RAG baseline" text={answers.baseline.answer} caption="Similarity-based repository context" /></div>}</div>
}

function AnswerCard({ title, text, caption }) {
  return <article className="answer-card"><span className="result-label">{title}</span><p>{text}</p><small>{caption}</small></article>
}

export default RagVsGraph
