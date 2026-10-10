/**
 * The client's copy of the server's upload rules.
 *
 * <p>Checking here is a courtesy, not a control: it turns a round trip and a
 * 400 into instant feedback. The server applies the same allowlist and size
 * limit regardless of what the browser does, so this list being out of date
 * can only ever make the UI stricter or slightly less helpful, never less safe.
 */
export const MAX_UPLOAD_BYTES = 25 * 1024 * 1024;

export const ALLOWED_CONTENT_TYPES: ReadonlySet<string> = new Set([
  'application/pdf',
  'image/png',
  'image/jpeg',
  'image/gif',
  'image/webp',
  'text/plain',
  'text/csv',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.ms-excel',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/vnd.ms-powerpoint',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation',
]);

/** Returns a human-readable problem, or null when the file is acceptable. */
export function validateFile(file: File): string | null {
  if (!ALLOWED_CONTENT_TYPES.has(file.type)) {
    return `${file.type || 'This file type'} is not accepted. Allowed: PDF, images, text, CSV, Word, Excel and PowerPoint.`;
  }
  if (file.size > MAX_UPLOAD_BYTES) {
    return `This file is ${(file.size / 1024 / 1024).toFixed(1)} MB. The limit is 25 MB.`;
  }
  if (file.size === 0) {
    return 'This file is empty.';
  }
  return null;
}
