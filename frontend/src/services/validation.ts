export const trimJava = (value: string) => value.replace(/^[\u0000-\u0020]+|[\u0000-\u0020]+$/g, "");
export function textLength(value: string, min: number, max: number, label: string, trim = true) {
  const normalized = trim ? trimJava(value) : value;
  const size = Array.from(normalized).length;
  if (
    size < min ||
    size > max ||
    (min > 0 && !normalized.trim()) ||
    /[\u0000\uD800-\uDFFF]/u.test(normalized)
  )
    throw new Error(`${label}: debe tener entre ${min} y ${max} caracteres Unicode.`);
  return normalized;
}
export function displayName(value: string) {
  const name = textLength(value.trim(), 2, 120, "Nombre");
  if (/\p{Cc}/u.test(name)) throw new Error("El nombre no admite caracteres de control.");
  return name;
}
export function password(value: string) {
  if (value.length < 8 || new TextEncoder().encode(value).length > 72)
    throw new Error("La contraseña debe tener al menos 8 caracteres y como máximo 72 bytes UTF-8.");
  return value;
}
export function nonEmptyPatch(patch: object) {
  if (!Object.keys(patch).length) throw new Error("No hay cambios para guardar.");
}
