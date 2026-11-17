export function header(response, name) {
  const headers = response && response.headers;
  if (!headers || typeof headers !== "object") return "";
  const keys = Object.keys(headers).filter((key) => key.toLowerCase() === name.toLowerCase());
  return keys.length === 1 && typeof headers[keys[0]] === "string" ? headers[keys[0]] : "";
}
