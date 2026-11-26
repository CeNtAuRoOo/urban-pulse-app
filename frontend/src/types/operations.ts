export interface Review {
  id: string;
  incidentId: string;
  decision: string;
  reason: string | null;
  reviewedAt: string;
  reviewedBy: { id: string; displayName: string };
  resultingStatus: string | null;
}
export interface PriorityDecision {
  id: string;
  incidentId: string;
  priority: string;
  source: string;
  justification: string;
  decidedAt: string;
  decidedBy: { id: string; displayName: string };
}
export interface InformationResponse {
  id: string;
  incidentId: string;
  reviewId: string;
  message: string;
  respondedAt: string;
  respondedBy: { id: string; displayName: string };
}
export interface ReviewOutcome {
  incidentId: string;
  status: string;
  informationPending: boolean;
  latestReview:
    | (Omit<Review, "id" | "incidentId" | "reviewedBy"> & {
        responsible: string;
      })
    | null;
}
export interface Attachment {
  id: string;
  incidentId: string;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  uploadedBy: string | null;
  createdAt: string;
  description: string | null;
}
export interface Duplicate {
  candidateIncidentId: string;
  score: number;
  reasons: string[];
}
export interface MapIncident {
  type: "INCIDENT";
  incidentId: string;
  title: string;
  status: string;
  category?: { id: string; name: string };
  priority?: string;
  createdAt: string;
  coordinates: { latitude: number; longitude: number };
  coordinatePrecision: "EXACT" | "APPROXIMATE";
  canAccessPrivateDetail: boolean;
}
export interface MapCluster {
  type: "CLUSTER";
  clusterId: string;
  coordinates: { latitude: number; longitude: number };
  incidentCount: number;
}
export type MapFeature = MapIncident | MapCluster;
export interface MapResult {
  items: MapFeature[];
  count: number;
  totalIncidents: number;
}
export interface UrbanAsset {
  id: number;
  sourceTable: string;
  name: string | null;
  category: string | null;
  description: string | null;
  geom: string;
  address: null;
  phone: null;
  email: null;
  url: null;
}
