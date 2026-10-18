import { useState } from 'react'
import { reviewPr } from '../api'
import Spinner from './Spinner'

function reviewerChips(value) {
  if (!value || value === 'No reviewer history found.') return []
  return String(value).split(';').map((item) => {
    const match = item.trim().match(/^(.+?)\s+\((\d+)\s+shared paths\)$/)
    return { name: match ? match[1] : item.trim(), score: match ? match[2] : null }
  }).filter((item) => item.name)
}

function conflictLines(value) {
  if (!value || value === 'No recorded conflicts found.') return []
  return String(value).split(';').map((item) => item.trim()).filter(Boolean)
}

function PRPanel({ onPathChange, onHandoffs }) {
  const [prNumber, setPrNumber] = useState('')
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const submit = async (event) => {
    event.preventDefault()
    setError('')
    setLoading(true)
    try {
      const response = await reviewPr(prNumber)
      setResult(response)
      onPathChange(response.pathNodeIds || [])
      onHandoffs(response.handoffs || [])
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setLoading(false)
    }
  }

  const reviewers = reviewerChips(result?.reviewers)
  const conflicts = conflictLines(result?.conflicts)

  return <div className="tool-panel">
    <div className="panel-heading"><div><span className="eyebrow">Pull request review</span><h1>Review a PR</h1></div></div>
    <p className="panel-description">Find likely reviewers and surface decisions that may conflict with this change.</p>
    <form onSubmit={submit} className="form-row"><input type="number" min="1" value={prNumber} onChange={(event) => setPrNumber(event.target.value)} placeholder="Pull request number" /><button type="submit" disabled={loading || !prNumber}>{loading ? <Spinner /> : 'Review'}</button></form>
    {error && <p className="error" role="alert">{error}</p>}
    {result && <div className="review-results">
      <section><span className="result-label">Suggested reviewers</span>{reviewers.length ? <div className="chip-row">{reviewers.map((reviewer) => <span className="chip" key={reviewer.name}>{reviewer.name}{reviewer.score && <small>{reviewer.score} paths</small>}</span>)}</div> : <p className="muted">No reviewer history found.</p>}</section>
      <section className={conflicts.length ? 'warning-card' : 'quiet-card'}><span className="result-label">Decision conflicts</span>{conflicts.length ? conflicts.map((conflict) => <p key={conflict}>{conflict}</p>) : <p>No recorded conflicts found for this pull request.</p>}</section>
      <CopyBox value={result.draftComment} />
    </div>}
  </div>
}

function CopyBox({ value }) {
  const [copied, setCopied] = useState(false)
  const copy = async () => {
    await navigator.clipboard.writeText(value)
    setCopied(true)
    window.setTimeout(() => setCopied(false), 1600)
  }
  return <section className="copy-box"><div className="copy-heading"><span className="result-label">Draft review comment</span><button type="button" className="ghost-button" onClick={copy}>{copied ? 'Copied' : 'Copy'}</button></div><p>{value}</p></section>
}

export default PRPanel
