/** Shapes returned by the API. Kept in one place so a contract change is one edit. */

export type Role = 'ADMIN' | 'MANAGER' | 'EMPLOYEE';

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  userId: number;
  name: string;
  email: string;
  role: Role;
}

export interface UserProfile {
  id: number;
  name: string;
  email: string;
  role: Role;
  enabled: boolean;
  createdAt: string;
}

export interface DocumentSummary {
  id: number;
  title: string;
  description: string | null;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  checksum: string;
  ownerId: number;
  ownerName: string;
  categoryId: number | null;
  categoryName: string | null;
  versionCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface DocumentVersion {
  id: number;
  documentId: number;
  versionNumber: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  checksum: string;
  uploadedById: number;
  uploadedByName: string;
  createdAt: string;
}

export interface Category {
  id: number;
  name: string;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export type AuditAction =
  | 'USER_REGISTERED'
  | 'USER_LOGGED_IN'
  | 'USER_ROLE_CHANGED'
  | 'USER_DISABLED'
  | 'DOCUMENT_UPLOADED'
  | 'DOCUMENT_UPDATED'
  | 'DOCUMENT_DOWNLOADED'
  | 'DOCUMENT_DELETED'
  | 'VERSION_CREATED'
  | 'VERSION_RESTORED'
  | 'CATEGORY_CREATED'
  | 'CATEGORY_UPDATED'
  | 'CATEGORY_DELETED';

export interface AuditEntry {
  id: number;
  action: AuditAction;
  description: string;
  actorId: number | null;
  actorName: string;
  documentId: number | null;
  createdAt: string;
}

export interface DashboardStats {
  documentCount: number;
  totalBytes: number;
  categoryCount: number;
  userCount: number;
  byFileType: { contentType: string; count: number }[];
}

/** Spring Data's page envelope. */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

/** RFC 9457 problem+json, which is what every API error now returns. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  errors?: Record<string, string>;
}
