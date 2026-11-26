import { isPickerCacheCopy, TemporaryFile } from "../services/temporary-file";
import * as FileSystem from "expo-file-system/legacy";
import * as Sharing from "expo-sharing";
export async function imageUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(new Error("No se pudo visualizar la imagen."));
    reader.readAsDataURL(blob);
  });
}
export function releaseImage(_uri: string) {}
export async function saveImage(blob: Blob, name: string) {
  if (!(await Sharing.isAvailableAsync())) throw new Error("Este dispositivo no permite compartir archivos.");
  const data = await imageUrl(blob);
  const uri = `${FileSystem.cacheDirectory}urbanpulse-${Date.now()}-${name.replace(/[^a-zA-Z0-9._-]/g, "_")}`;
  try {
    await FileSystem.writeAsStringAsync(uri, data.slice(data.indexOf(",") + 1), {
      encoding: FileSystem.EncodingType.Base64,
    });
    await Sharing.shareAsync(uri, { mimeType: blob.type });
  } finally {
    await FileSystem.deleteAsync(uri, { idempotent: true });
  }
}

/** Called only for fresh results from DocumentPicker with copyToCacheDirectory enabled. */
export function pickerTemporaryFile(uri: string) {
  const owned = isPickerCacheCopy(uri, FileSystem.cacheDirectory);
  return new TemporaryFile(async () => {
    if (!owned) return;
    const info = await FileSystem.getInfoAsync(uri);
    if (info.exists && !info.isDirectory) await FileSystem.deleteAsync(uri, { idempotent: true });
  });
}
