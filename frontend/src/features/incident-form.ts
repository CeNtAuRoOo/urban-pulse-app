import type { Incident, IncidentInput } from "../types/api";
import { nonEmptyPatch, textLength } from "../services/validation";
export interface IncidentFields {
  title: string;
  description: string;
  latitude: string;
  longitude: string;
  accuracy: string;
  categoryId: string;
  categoryMode: string;
}
export function incidentFields(incident?: Incident): IncidentFields {
  const coordinates = incident?.location.coordinates;
  return {
    title: incident?.title || "",
    description: incident?.description || "",
    latitude: coordinates ? String(coordinates.latitude) : "",
    longitude: coordinates ? String(coordinates.longitude) : "",
    accuracy: coordinates?.accuracyMeters == null ? "" : String(coordinates.accuracyMeters),
    categoryId: incident?.category?.id || "",
    categoryMode: "keep",
  };
}
export function incidentBody(fields: IncidentFields, original?: Incident): Partial<IncidentInput> {
  const title = textLength(fields.title, 5, 150, "Título");
  const description = textLength(fields.description, 10, 5000, "Descripción");
  const latitude = Number(fields.latitude);
  const longitude = Number(fields.longitude);
  if (
    !fields.latitude.trim() ||
    !fields.longitude.trim() ||
    !Number.isFinite(latitude) ||
    latitude < -90 ||
    latitude > 90 ||
    !Number.isFinite(longitude) ||
    longitude < -180 ||
    longitude > 180
  )
    throw new Error("Indica coordenadas válidas: latitud −90…90 y longitud −180…180.");
  const accuracy = fields.accuracy.trim() ? Number(fields.accuracy) : undefined;
  if (accuracy !== undefined && (!Number.isFinite(accuracy) || accuracy < 0))
    throw new Error("La precisión debe ser un número no negativo.");
  const coordinates = {
    latitude,
    longitude,
    ...(accuracy === undefined ? {} : { accuracyMeters: accuracy }),
  };
  if (!original)
    return {
      title,
      description,
      coordinates,
      ...(fields.categoryId ? { categoryId: fields.categoryId } : {}),
    };
  const patch: Partial<IncidentInput> = {};
  if (title !== original.title) patch.title = title;
  if (description !== original.description) patch.description = description;
  const before = original.location.coordinates;
  if (
    latitude !== before.latitude ||
    longitude !== before.longitude ||
    accuracy !== (before.accuracyMeters ?? undefined)
  )
    patch.coordinates = coordinates;
  if (fields.categoryMode === "remove" && original.category) patch.categoryId = null;
  if (fields.categoryMode === "change") {
    if (!fields.categoryId) throw new Error("Selecciona una categoría activa.");
    if (fields.categoryId !== original.category?.id) patch.categoryId = fields.categoryId;
  }
  nonEmptyPatch(patch);
  return patch;
}
