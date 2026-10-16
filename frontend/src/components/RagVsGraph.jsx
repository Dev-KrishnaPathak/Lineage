import { useState } from 'react'
import { ask, askBaseline } from '../api'

function RagVsGraph({ onPathChange }) {
  const [question, setQuestion] = useState('What should I work on next?')
  const [answers, setAnswers] = useState(null)

  const submit = async (event) => {
    event.preventDefault()
    const [graph, baseline] = await Promise.all([ask(question), askBaseline(question)])
    setAnswers({ graph, baseline })
    onPathChange(graph.pathNodeIds || [])
  }

  return (
    <section className="panel">
      <div className="section-heading"><span className="eyebrow">COMPARISON</span><h2>RAG vs graph</h2></div>
      <form onSubmit={submit} className="form-row"><input value={question} onChange={(event) => setQuestion(event.target.value)} /><button type="submit">Compare</button></form>
      {answers && <div className="comparison"><div><h3>Graph</h3><p>{answers.graph.answer}</p></div><div><h3>RAG baseline</h3><p>{answers.baseline.answer}</p></div></div>}
    </section>
  )
}

export default RagVsGraph
