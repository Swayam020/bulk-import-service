import { useState, type FormEvent } from 'react'
import { uploadImport } from '../api/client'
import type { ImportAccepted } from '../api/types'

// Same limit as spring.servlet.multipart.max-file-size: checking here saves sending
// 50 MB only to be refused with a 413.
const MAX_UPLOAD_BYTES = 50 * 1024 * 1024

interface UploadFormProps {
  onAccepted: (job: ImportAccepted) => void
}

export function UploadForm({ onAccepted }: UploadFormProps) {
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (file === null) {
      setError('choose a CSV file first')
      return
    }
    if (file.size > MAX_UPLOAD_BYTES) {
      setError('file exceeds the 50MB upload limit')
      return
    }
    setError(null)
    setUploading(true)
    try {
      onAccepted(await uploadImport(file))
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'request failed')
      setUploading(false)
    }
  }

  return (
    <form onSubmit={handleSubmit}>
      <label>
        CSV file
        <input
          type="file"
          accept=".csv"
          onChange={(event) => setFile(event.target.files?.[0] ?? null)}
        />
      </label>
      <p className="hint">Columns: id,name,email,amount. Up to 50 MB.</p>
      {error !== null && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <button type="submit" disabled={uploading}>
        {uploading ? 'Uploading…' : 'Upload'}
      </button>
    </form>
  )
}
