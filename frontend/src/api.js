const API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/+$/, '')

async function request(path, options = {}, timeoutMs = 30000) {
  let response
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), timeoutMs)
  try {
    response = await fetch(`${API_URL}${path}`, {
      headers: { 'Content-Type': 'application/json' },
      signal: controller.signal,
      ...options,
    })
  } catch (error) {
    if (error.name === 'AbortError') {
      throw new Error('GitHub import is taking too long. Check the repository URL and token, then try again.')
    }
    throw new Error('Backend is unavailable. Start the Lineage backend on port 8080.')
  } finally {
    clearTimeout(timeout)
  }
  if (!response.ok) {
    let detail = ''
    try {
      const body = await response.json()
      detail = body.message ? `: ${body.message}` : ''
    } catch {
      // The server may return an empty or non-JSON error response.
    }
    const statusMessage = response.status === 429
      ? 'GitHub rate limit reached. Set GITHUB_TOKEN or GH_TOKEN and restart the backend.'
      : response.status === 504
        ? 'GitHub request timed out.'
        : `Request failed (${response.status})`
    throw new Error(detail && detail.slice(2) === statusMessage
      ? statusMessage
      : `${statusMessage}${detail}`)
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
export const getNodeDetails = (nodeId) => request(`/node/${nodeId}`)
export const ingest = (repository) => request('/ingest', {
  method: 'POST',
  body: JSON.stringify({ repository }),
}, 90000)
export const getIngestStatus = () => request('/ingest/status')
