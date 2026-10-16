const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080'

async function request(path, options = {}) {
  const response = await fetch(`${API_URL}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`)
  }
  return response.json()
}

export const getGraph = () => request('/graph')
export const ask = (question) => request('/ask', {
  method: 'POST',
  body: JSON.stringify({ question }),
})
export const reviewPr = (prNumber) => request('/pr', {
  method: 'POST',
  body: JSON.stringify({ prNumber: Number(prNumber) }),
})
export const recommend = (login) => request(`/recommend/${encodeURIComponent(login)}`)
export const askBaseline = (question) => request('/ask/baseline', {
  method: 'POST',
  body: JSON.stringify({ question }),
})
export const simulate = (nodeId) => request(`/simulate/${nodeId}`, { method: 'POST' })
