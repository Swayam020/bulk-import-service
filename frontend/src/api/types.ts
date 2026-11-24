// Mirrors src/main/java/com/swayam/bulkimport/api/Dtos.java. Java longs arrive as JSON
// numbers, exact up to 2^53 rows, and Instants as ISO-8601 strings.

export type ImportStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'

export interface ImportAccepted {
  jobId: string
  status: ImportStatus
  filename: string
  sizeBytes: number
}

export interface ImportJobView {
  jobId: string
  status: ImportStatus
  filename: string
  sizeBytes: number
  rowsProcessed: number
  rowsSucceeded: number
  rowsFailed: number
  errorMessage: string | null
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
}

export interface ApiError {
  error: string
  message: string
  timestamp: string
}
