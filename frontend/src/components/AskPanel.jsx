import { useState } from 'react'
import { ask } from '../api'
import Spinner from './Spinner'

const EXAMPLES = ['Who reviewed PR #483?', 'Why was caching decided?', 'What should demo-user work on next?']

function AskPanel({ onPathChange }) {
  const [question, setQuestion] = useState('')
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const submit = async (event) => {
    event.preventDefault()
    if (!question.trim()) return
    setError('')
    setLoading(true)
    try {
      const response = await ask(question.trim())
      setResult(response)
      onPathChange(response.pathNodeIds || [])
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setLoading(false)
    }
  }

  return <div className="tool-panel"><div className="panel-heading"><div><span className="eyebrow">Graph query</span><h1>Ask your repository</h1></div></div><p className="panel-description">Ask about reviewers, decisions, ownership, or the next connected issue.</p><div className="example-row">{EXAMPLES.map((example) => <button className="example-chip" key={example} onClick={() => setQuestion(example)}>{example}</button>)}</div><form onSubmit={submit} className="ask-form"><input value={question} onChange={(event) => setQuestion(event.target.value)} placeholder="Ask a question about this graph…" /><button type="submit" disabled={loading || !question.trim()}>{loading ? <Spinner /> : 'Ask'}</button></form>{error && <p className="error" role="alert">{error}</p>}{result && <div className="ask-result"><div className="answer-card"><span className="result-label">Answer</span><p>{result.answer}</p></div>{result.pathNodeIds?.length > 0 && <div><span className="result-label">Source nodes</span><div className="chip-row">{result.pathNodeIds.map((id) => <button className="source-chip" key={id} onClick={() => onPathChange([id])}>Node {id}</button>)}</div></div>}</div>}</div>
}

export default AskPanel
