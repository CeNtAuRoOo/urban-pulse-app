import { API_URL } from "../config/api";

export type Query = Record<string, string | number | boolean | undefined>;
export interface ApiResponse<T> {
  data: T;
  status: number;
  headers: Headers;
  etag: string | null;
  location: string | null;
  retryAfter: string | null;
}
const messages: Record<number, string> = {
  400: "Revisa los parámetros de la petición.",
  401: "La sesión no es válida. Inicia sesión de nuevo.",
  403: "Tu cuenta no tiene permiso para esta operación.",
  404: "Recurso no encontrado o no disponible para tu cuenta.",
  409: "La operación no es compatible con el estado actual.",
  412: "El recurso ha cambiado. Recarga el detalle antes de corregirlo.",
  422: "Revisa los campos del formulario.",
  428: "Falta la ETag. Recarga el detalle antes de corregirlo.",
  429: "Demasiadas peticiones. Espera antes de volver a intentarlo.",
  503: "Servicio temporalmente no disponible.",
};
export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    detail?: string,
    public retryAfter: string | null = null
  ) {
    super(
      status === 404
        ? messages[404]
        : [messages[status], detail].filter(Boolean).join(" ") || "No se pudo completar la petición."
    );
  }
}
export interface RequestOptions {
  method?: "GET" | "POST" | "PATCH";
  body?: unknown;
  query?: Query;
  etag?: string;
  signal?: AbortSignal;
}
export async function request<T>(
  path: string,
  options: RequestOptions = {},
  token?: string
): Promise<ApiResponse<T>> {
  const url = new URL(`${API_URL}${path}`);
  Object.entries(options.query || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== "") url.searchParams.set(key, String(value));
  });
  const headers = new Headers({ Accept: "application/json" });
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (options.body !== undefined) headers.set("Content-Type", "application/json");
  if (options.etag !== undefined) headers.set("If-Match", options.etag);
  let response: Response;
  try {
    response = await fetch(url, {
      method: options.method || "GET",
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
      cache: "no-store",
    });
  } catch (error) {
    if (error instanceof Error && error.name === "AbortError") throw error;
    throw new ApiError(
      0,
      "CONNECTION_ERROR",
      "No se pudo conectar con la API. Comprueba el servicio, la URL y el origen CORS."
    );
  }
  const raw = await response.text();
  let data: unknown;
  try {
    data = raw ? JSON.parse(raw) : undefined;
  } catch {
    data = undefined;
  }
  if (!response.ok) {
    const problem = data as { code?: string; detail?: string } | undefined;
    throw new ApiError(
      response.status,
      typeof problem?.code === "string" ? problem.code : "HTTP_ERROR",
      response.status < 500 && typeof problem?.detail === "string" ? problem.detail : undefined,
      response.headers.get("Retry-After")
    );
  }
  if (raw && data === undefined)
    throw new ApiError(response.status, "INVALID_RESPONSE", "La API no devolvió JSON válido.");
  return {
    data: data as T,
    status: response.status,
    headers: response.headers,
    etag: response.headers.get("ETag"),
    location: response.headers.get("Location"),
    retryAfter: response.headers.get("Retry-After"),
  };
}
