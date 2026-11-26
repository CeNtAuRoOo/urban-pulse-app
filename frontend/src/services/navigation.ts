/** Only internal, known origin screens can be restored from an incident URL. */
const origins: Record<string, string> = {
  "/": "Volver al inicio",
  "/incidents": "Volver a mis incidencias",
  "/incidents/all": "Volver a gestión municipal",
  "/map": "Volver al mapa",
};
export function returnTarget(value: unknown): { href: string; label: string } {
  const raw = typeof value === "string" ? value : "/incidents";
  const path = raw.split("?")[0];
  return Object.prototype.hasOwnProperty.call(origins, path) && !raw.includes("#") && !raw.includes("\\")
    ? { href: raw, label: origins[path] }
    : { href: "/incidents", label: origins["/incidents"] };
}
export function routeKey(path: string, params: Record<string, string | string[] | undefined>) {
  const query = new URLSearchParams();
  Object.keys(params)
    .sort()
    .forEach((key) => {
      const value = params[key];
      if (key !== "id" && typeof value === "string" && value) query.set(key, value);
    });
  return `${path}${query.size ? "?" + query.toString() : ""}`;
}
export function incidentHref(id: string, origin: string) {
  return `/incidents/${encodeURIComponent(id)}?from=${encodeURIComponent(returnTarget(origin).href)}`;
}
export function createIncidentHref(origin: string): `/incidents/new?from=${string}` {
  return `/incidents/new?from=${encodeURIComponent(returnTarget(origin).href)}`;
}
export const scrollPositions = new Map<string, number>();
