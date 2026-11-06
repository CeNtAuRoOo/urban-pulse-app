export type Role = "CITIZEN" | "ADMIN" | "OPERATOR" | "TECHNICIAN" | "DEPARTMENT_COORDINATOR";
export interface Category {
  id: string;
  name: string;
  description: string | null;
  active: boolean;
}
export interface Department extends Category {
  code: string;
}
export interface User {
  id: string;
  displayName: string;
  email: string;
  emailVerified: boolean;
  active: boolean;
  createdAt: string;
  roles: Role[];
  departmentMemberships: { department: Department; role: Role; joinedAt: string; leftAt: string | null }[];
}
export interface TokenDTO {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}
export interface Pagination {
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
}
export interface Page<T> {
  data: T[];
  pagination: Pagination;
}
export interface Coordinates {
  latitude: number;
  longitude: number;
  accuracyMeters?: number;
}
export interface IncidentSummary {
  id: string;
  title: string;
  category?: Category;
  location: { coordinates: Coordinates };
  status: string;
  priority?: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}
export interface Incident extends IncidentSummary {
  description: string;
  reporter?: { id: string; displayName: string };
  assignedDepartment?: Department;
  resolvedAt?: string;
  closedAt?: string;
}
export interface IncidentInput {
  title: string;
  description: string;
  coordinates: Coordinates;
  categoryId?: string | null;
}
export interface HistoryEvent {
  id: string;
  eventType: string;
  occurredAt: string;
  actor?: { id: string; displayName: string };
  summary: string;
  details: Record<string, string>;
}
export interface Invitation {
  id: string;
  email: string;
  role: Role;
  departmentId?: string;
  status: string;
  createdBy: string;
  createdAt: string;
  expiresAt: string;
  redeemedAt?: string;
  revokedAt?: string;
  redeemedBy?: string;
  revokedBy?: string;
}
