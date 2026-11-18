// Tipos compartidos sin configuración ni imports de k6: verificables completamente offline.
export const states = [
  "REPORTED",
  "AWAITING_INFORMATION",
  "VALIDATED",
  "REJECTED",
  "ASSIGNED_TO_DEPARTMENT",
  "WORK_ASSIGNED",
  "IN_PROGRESS",
  "RESOLVED",
  "CLOSED",
];
export const priorities = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];
export const decisions = ["VALIDATE", "REJECT", "REQUEST_INFORMATION"];

export function isUuid(value) {
  return (
    typeof value === "string" && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)
  );
}

export const has = (value, key) => Object.prototype.hasOwnProperty.call(value, key);
export const object = (value) => value !== null && typeof value === "object" && !Array.isArray(value);
export const text = (value) => typeof value === "string";

export function shape(value, required, optional = []) {
  return (
    object(value) &&
    required.every((key) => has(value, key)) &&
    Object.keys(value).every((key) => required.includes(key) || optional.includes(key))
  );
}

export function timestamp(value) {
  if (!text(value)) return false;
  const match =
    /^(\d{4})-(\d{2})-(\d{2})T([01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d{1,9})?(?:Z|[+-](?:[01]\d|2[0-3]):[0-5]\d)$/.exec(
      value
    );
  if (!match || !Number.isFinite(Date.parse(value))) return false;
  const [, year, month, day] = match.map(Number);
  const leap = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  const days = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  return month >= 1 && month <= 12 && day >= 1 && day <= days[month - 1];
}
