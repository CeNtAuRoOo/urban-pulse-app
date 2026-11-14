const configuredUrl = (process.env.EXPO_PUBLIC_API_URL || "http://localhost:8080").replace(/\/+$/, "");
export const API_URL = configuredUrl.endsWith("/api/v1") ? configuredUrl : `${configuredUrl}/api/v1`;
