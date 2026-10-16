import { useState } from 'react'
import { reviewPr } from '../api'

function PRPanel({ onPathChange, onHandoffs }) {
  const [prNumber, setPrNumber] = useState('2')
  const [result, setResult] = useState(null)

  const submit = async (event) => {
    event.preventDefault()
    const response = await reviewPr(prNumber)
    setResult(response)
    onPathChange(response.pathNodeIds || [])
    onHandoffs(response.handoffs || [])
  }

  return (
    <section className="panel">
      <div className="section-heading"><span className="eyebrow">PR REVIEW</span><h2>Review a pull request</h2></div>
      <form onSubmit={submit} className="form-row">
        <input type="number" min="1" value={prNumber} onChange={(event) => setPrNumber(event.target.value)} />
        <button type="submit">Review</button>
      </form>
      {result && <div className="result">
        <h3>Reviewers</h3><pre>{JSON.stringify(result.reviewers, null, 2)}</pre>
        <h3>Conflicts</h3><pre>{JSON.stringify(result.conflicts, null, 2)}</pre>
        <h3>Draft comment</h3><p>{result.draftComment}</p>
      </div>}
    </section>
  )
}

export default PRPanel
