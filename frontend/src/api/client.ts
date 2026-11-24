import type { ApiError, ImportAccepted, ImportJobView } from './types'

const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

/**
 * Every non-ok response is an ApiError from the shared exception handler, so the
 * server's own words become the message the UI shows. Anything that is not that
 * JSON (a proxy's error page, a truncated body) has no message to borrow.
 */
async function toFailure(response: Response): Promise<Error> {
  let message: string
  try {
    message = ((await response.json()) as ApiError).message
  } catch {
    return new Error('request failed')
  }
  return new Error(message)
}

/** No Content-Type header: the browser must write it, because only it knows the multipart boundary. */
export async function uploadImport(file: File): Promise<ImportAccepted> {
  const body = new FormData()
  body.append('file', file)
  const response = await fetch(`${API_BASE_URL}/api/imports`, { method: 'POST', body })
  if (!response.ok) {
    throw await toFailure(response)
  }
  return (await response.json()) as ImportAccepted
}

export async function getImport(jobId: string): Promise<ImportJobView> {
  const response = await fetch(`${API_BASE_URL}/api/imports/${jobId}`)
  if (!response.ok) {
    throw await toFailure(response)
  }
  return (await response.json()) as ImportJobView
}

/**
 * A link rather than a fetch: the server marks the report as an attachment, so the
 * browser streams it straight to disk, and a plain navigation needs no CORS.
 */
export function errorReportUrl(jobId: string): string {
  return `${API_BASE_URL}/api/imports/${jobId}/errors`
}
