import { useEffect, useState } from 'react'
import { errorReportUrl, getImport } from '../api/client'
import type { ImportJobView } from '../api/types'

interface JobStatusProps {
  jobId: string
  /** Matches the worker's own poll delay; polling faster only re-reads the same row. */
  pollMs?: number
}

const isFinished = (job: ImportJobView) => job.status === 'COMPLETED' || job.status === 'FAILED'
const count = (n: number) => n.toLocaleString('en-US')

export function JobStatus({ jobId, pollMs = 1000 }: JobStatusProps) {
  const [job, setJob] = useState<ImportJobView | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    // The next poll is scheduled only after a response arrives, so a slow server
    // never has two status requests in flight for the same job.
    let cancelled = false
    let timer: ReturnType<typeof setTimeout> | undefined
    const poll = () => {
      getImport(jobId)
        .then((latest) => {
          if (cancelled) return
          setJob(latest)
          if (!isFinished(latest)) timer = setTimeout(poll, pollMs)
        })
        .catch((e: unknown) => {
          if (!cancelled) setError(e instanceof Error ? e.message : 'request failed')
        })
    }
    poll()
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [jobId, pollMs])

  if (error !== null) {
    return (
      <p className="error" role="alert">
        {error}
      </p>
    )
  }
  if (job === null) {
    return <p>Loading import…</p>
  }
  return (
    <section>
      <h2>{job.filename}</h2>
      <dl>
        <dt>Status</dt>
        <dd>{job.status}</dd>
        <dt>Processed</dt>
        <dd>{count(job.rowsProcessed)}</dd>
        <dt>Succeeded</dt>
        <dd>{count(job.rowsSucceeded)}</dd>
        <dt>Failed</dt>
        <dd>{count(job.rowsFailed)}</dd>
      </dl>
      {job.errorMessage !== null && (
        <p className="error" role="alert">
          {job.errorMessage}
        </p>
      )}
      {isFinished(job) && job.rowsFailed > 0 && (
        <a href={errorReportUrl(job.jobId)}>Download error report</a>
      )}
      {job.status === 'COMPLETED' && job.rowsFailed === 0 && <p>No failed rows.</p>}
    </section>
  )
}
