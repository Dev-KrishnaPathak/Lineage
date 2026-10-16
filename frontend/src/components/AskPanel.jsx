import { useState } from 'react'
import { ask } from '../api'

function AskPanel({ onPathChange }) {
  const [question, setQuestion] = useState('')
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')

  const submit = async (event) => {
    event.preventDefault()
    setError('')
    try {
      const response = await ask(question)
      setResult(response)
      onPathChange(response.pathNodeIds || [])
    } catch (requestError) {
      setError(requestError.message)
    }
  }

  return (
    <section className="panel">
      <div className="section-heading"><span className="eyebrow">ASK LINEAGE</span><h2>Ask the graph</h2></div>
      <form onSubmit={submit} className="form-row">
        <input value={question} onChange={(event) => setQuestion(event.target.value)} placeholder="Why was caching decided?" />
        <button type="submit">Ask</button>
      </form>
      {error && <p className="error">{error}</p>}
      {result && <div className="result"><p>{result.answer}</p><button type="button" className="link-button" onClick={() => onPathChange(result.pathNodeIds)}>Highlight sources ({result.pathNodeIds.length})</button></div>}
    </section>
  )
}

export default AskPanel
