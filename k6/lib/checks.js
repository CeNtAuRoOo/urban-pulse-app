import { decisions, has, isUuid, priorities, shape, states, text, timestamp } from "./contracts.js";
import { header } from "./headers.js";

export const strongEtag = (response) => /^"sha256-[0-9a-f]{64}"$/.test(header(response, "ETag"));
export const userSummary = (value) =>
  shape(value, ["id", "displayName"]) && isUuid(value.id) && text(value.displayName);

export function coordinates(value, allowAccuracy = true) {
  return (
    shape(value, ["latitude", "longitude"], allowAccuracy ? ["accuracyMeters"] : []) &&
    Number.isFinite(value.latitude) &&
    Math.abs(value.latitude) <= 90 &&
    Number.isFinite(value.longitude) &&
    Math.abs(value.longitude) <= 180 &&
    (!has(value, "accuracyMeters") || (Number.isFinite(value.accuracyMeters) && value.accuracyMeters >= 0))
  );
}

const location = (value) => shape(value, ["coordinates"]) && coordinates(value.coordinates);
const category = (value) =>
  shape(value, ["id", "name", "description", "active"]) &&
  isUuid(value.id) &&
  text(value.name) &&
  (value.description === null || text(value.description)) &&
  typeof value.active === "boolean";
const department = (value) =>
  shape(value, ["id", "name", "code", "description", "active"]) &&
  isUuid(value.id) &&
  text(value.name) &&
  text(value.code) &&
  (value.description === null || text(value.description)) &&
  typeof value.active === "boolean";
export const catalog = (data, kind = "category") =>
  Array.isArray(data) && data.every(kind === "department" ? department : category);

export function userProfile(data, actor) {
  return (
    shape(data, [
      "id",
      "displayName",
      "email",
      "emailVerified",
      "active",
      "createdAt",
      "roles",
      "departmentMemberships",
    ]) &&
    isUuid(data.id) &&
    data.id === actor.subject &&
    text(data.displayName) &&
    text(data.email) &&
    data.active === true &&
    data.emailVerified === true &&
    timestamp(data.createdAt) &&
    Array.isArray(data.roles) &&
    data.roles.every((role) => ["CITIZEN", "OPERATOR", "ADMIN"].includes(role)) &&
    new Set(data.roles).size === data.roles.length &&
    data.roles.includes("CITIZEN") &&
    data.roles.includes(actor.role) &&
    (actor.role !== "CITIZEN" || !data.roles.some((role) => ["ADMIN", "OPERATOR"].includes(role))) &&
    Array.isArray(data.departmentMemberships) &&
    data.departmentMemberships.every(
      (membership) =>
        shape(membership, ["department", "role", "joinedAt", "leftAt"]) &&
        department(membership.department) &&
        ["DEPARTMENT_COORDINATOR", "TECHNICIAN"].includes(membership.role) &&
        timestamp(membership.joinedAt) &&
        (membership.leftAt === null || timestamp(membership.leftAt))
    )
  );
}

const summaryRequired = ["id", "title", "status", "location", "createdAt", "updatedAt", "version"];
const summaryOptional = ["category", "priority"];
function summaryValues(item) {
  return (
    isUuid(item.id) &&
    text(item.title) &&
    states.includes(item.status) &&
    Number.isSafeInteger(item.version) &&
    item.version >= 0 &&
    timestamp(item.createdAt) &&
    timestamp(item.updatedAt) &&
    location(item.location) &&
    (!has(item, "category") || category(item.category)) &&
    (!has(item, "priority") || priorities.includes(item.priority))
  );
}
export const summary = (item) => shape(item, summaryRequired, summaryOptional) && summaryValues(item);

export function pageOf(data, page, pageSize, itemCheck) {
  if (
    !shape(data, ["data", "pagination"]) ||
    !Array.isArray(data.data) ||
    !shape(data.pagination, ["page", "pageSize", "totalItems", "totalPages"])
  )
    return false;
  const p = data.pagination;
  return (
    Number.isInteger(page) &&
    page >= 1 &&
    Number.isInteger(pageSize) &&
    pageSize >= 1 &&
    pageSize <= 100 &&
    p.page === page &&
    p.pageSize === pageSize &&
    Number.isSafeInteger(p.totalItems) &&
    p.totalItems >= 0 &&
    p.totalPages === Math.ceil(p.totalItems / pageSize) &&
    data.data.length === Math.min(pageSize, Math.max(0, p.totalItems - (page - 1) * pageSize)) &&
    data.data.every(itemCheck)
  );
}

export function incident(data, id, owner) {
  return (
    shape(
      data,
      [...summaryRequired, "description", "reporter"],
      [...summaryOptional, "assignedDepartment", "resolvedAt", "closedAt"]
    ) &&
    summaryValues(data) &&
    data.id === id &&
    text(data.description) &&
    userSummary(data.reporter) &&
    (!owner || data.reporter.id === owner) &&
    (!has(data, "assignedDepartment") || department(data.assignedDepartment)) &&
    ["resolvedAt", "closedAt"].every((key) => !has(data, key) || timestamp(data[key]))
  );
}

function historyDetails(type, details) {
  // IncidentService proyecta claves por evento; un mapa vacío histórico también es válido.
  if (type === "CREATED" || type === "UPDATED") {
    return (
      shape(details, [], ["version"]) &&
      (!has(details, "version") || (text(details.version) && /^[0-9]{1,19}$/.test(details.version)))
    );
  }
  if (type === "REVIEWED") {
    return (
      shape(details, [], ["decision", "reason"]) &&
      (!has(details, "decision") || decisions.includes(details.decision)) &&
      (!has(details, "reason") ||
        (["REJECT", "REQUEST_INFORMATION"].includes(details.decision) && text(details.reason)))
    );
  }
  if (type === "PRIORITY_CHANGED") {
    return (
      shape(details, [], ["priority"]) && (!has(details, "priority") || priorities.includes(details.priority))
    );
  }
  return type === "INFORMATION_RESPONDED" && shape(details, []);
}

export function historyItem(item, owner) {
  if (
    !shape(item, ["id", "eventType", "occurredAt", "summary", "details"], ["actor"]) ||
    !isUuid(item.id) ||
    !timestamp(item.occurredAt) ||
    !text(item.summary) ||
    !historyDetails(item.eventType, item.details)
  )
    return false;
  // NON_NULL omite actor; solo puede aparecer el informante original, nunca personal municipal.
  return (
    !has(item, "actor") ||
    (["CREATED", "UPDATED", "INFORMATION_RESPONDED"].includes(item.eventType) &&
      isUuid(owner) &&
      userSummary(item.actor) &&
      item.actor.id === owner)
  );
}

export function outcome(data, id) {
  if (
    !shape(data, ["incidentId", "status", "informationPending", "latestReview"]) ||
    !isUuid(data.incidentId) ||
    data.incidentId !== id ||
    !states.includes(data.status) ||
    data.informationPending !== (data.status === "AWAITING_INFORMATION")
  )
    return false;
  const review = data.latestReview;
  // ALWAYS conserva null en latestReview, reason y snapshots históricos resultingStatus.
  return (
    review === null ||
    (shape(review, ["decision", "reviewedAt", "resultingStatus", "reason", "responsible"]) &&
      decisions.includes(review.decision) &&
      timestamp(review.reviewedAt) &&
      review.responsible === "Ayuntamiento" &&
      (review.resultingStatus === null ||
        ["VALIDATED", "REJECTED", "AWAITING_INFORMATION"].includes(review.resultingStatus)) &&
      (review.reason === null || (review.decision !== "VALIDATE" && text(review.reason))))
  );
}

export function processingItem(resource, item) {
  const parent = (value) => isUuid(value.id) && isUuid(value.incidentId);
  if (resource === "reviews") {
    return (
      shape(item, [
        "id",
        "incidentId",
        "decision",
        "reason",
        "reviewedBy",
        "reviewedAt",
        "resultingStatus",
      ]) &&
      parent(item) &&
      decisions.includes(item.decision) &&
      (item.reason === null || text(item.reason)) &&
      userSummary(item.reviewedBy) &&
      timestamp(item.reviewedAt) &&
      (item.resultingStatus === null ||
        ["VALIDATED", "REJECTED", "AWAITING_INFORMATION"].includes(item.resultingStatus))
    );
  }
  if (resource === "priority-decisions") {
    return (
      shape(item, ["id", "incidentId", "priority", "source", "justification", "decidedBy", "decidedAt"]) &&
      parent(item) &&
      priorities.includes(item.priority) &&
      ["MANUAL", "ASSISTED"].includes(item.source) &&
      text(item.justification) &&
      userSummary(item.decidedBy) &&
      timestamp(item.decidedAt)
    );
  }
  return (
    resource === "information-responses" &&
    shape(item, ["id", "incidentId", "reviewId", "message", "respondedBy", "respondedAt"]) &&
    parent(item) &&
    isUuid(item.reviewId) &&
    text(item.message) &&
    userSummary(item.respondedBy) &&
    timestamp(item.respondedAt)
  );
}

export function duplicateCandidates(data, incidentId, expectedId) {
  return (
    Array.isArray(data) &&
    data.length <= 20 &&
    data.every(
      (item) =>
        shape(item, ["candidateIncidentId", "score", "reasons"]) &&
        isUuid(item.candidateIncidentId) &&
        item.candidateIncidentId !== incidentId &&
        Number.isFinite(item.score) &&
        item.score >= 0 &&
        item.score <= 1 &&
        Array.isArray(item.reasons) &&
        item.reasons.every(text)
    ) &&
    new Set(data.map((item) => item.candidateIncidentId)).size === data.length &&
    (expectedId === undefined ||
      (isUuid(expectedId) && data.some((item) => item.candidateIncidentId === expectedId)))
  );
}

function mapFeature(item) {
  if (!item || !coordinates(item.coordinates, false)) return false;
  if (item.type === "CLUSTER") {
    return (
      shape(item, ["type", "clusterId", "coordinates", "incidentCount"]) &&
      text(item.clusterId) &&
      /^g1:(?:[0-9]|1[0-7]):-?\d+:-?\d+$/.test(item.clusterId) &&
      Number.isSafeInteger(item.incidentCount) &&
      item.incidentCount >= 2
    );
  }
  return (
    item.type === "INCIDENT" &&
    shape(
      item,
      [
        "type",
        "incidentId",
        "title",
        "status",
        "createdAt",
        "coordinates",
        "coordinatePrecision",
        "canAccessPrivateDetail",
      ],
      ["category", "priority"]
    ) &&
    isUuid(item.incidentId) &&
    item.title === `Incidencia ${item.incidentId}` &&
    states.includes(item.status) &&
    timestamp(item.createdAt) &&
    typeof item.canAccessPrivateDetail === "boolean" &&
    item.coordinatePrecision === (item.canAccessPrivateDetail ? "EXACT" : "APPROXIMATE") &&
    (!has(item, "category") ||
      (shape(item.category, ["id", "name"]) && isUuid(item.category.id) && text(item.category.name))) &&
    (!has(item, "priority") || priorities.includes(item.priority))
  );
}

export function map(data, expectedId) {
  if (
    !shape(data, ["items", "count", "totalIncidents"]) ||
    !Array.isArray(data.items) ||
    data.count !== data.items.length ||
    data.count > 1000 ||
    !Number.isSafeInteger(data.totalIncidents) ||
    data.totalIncidents < 0 ||
    !data.items.every(mapFeature)
  )
    return false;
  const total = data.items.reduce((sum, item) => sum + (item.type === "CLUSTER" ? item.incidentCount : 1), 0);
  return (
    Number.isSafeInteger(total) &&
    data.totalIncidents === total &&
    (expectedId === undefined ||
      (isUuid(expectedId) && data.items.some((item) => item.incidentId === expectedId)))
  );
}
