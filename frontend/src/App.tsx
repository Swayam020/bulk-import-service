import { useState } from 'react'
import { UploadForm } from './components/UploadForm'
import { JobStatus } from './components/JobStatus'

function App() {
  const [jobId, setJobId] = useState<string | null>(null)

  return (
    <main>
      <h1>Bulk Import</h1>
      {jobId === null ? (
        <UploadForm onAccepted={(job) => setJobId(job.jobId)} />
      ) : (
        <>
          <JobStatus jobId={jobId} />
          <button type="button" className="secondary" onClick={() => setJobId(null)}>
            Upload another file
          </button>
        </>
      )}
    </main>
  )
}

export default App
