import { TemporaryFile } from "../services/temporary-file";
export async function imageUrl(blob: Blob): Promise<string> {
  return URL.createObjectURL(blob);
}
export function releaseImage(uri: string) {
  URL.revokeObjectURL(uri);
}
export async function saveImage(blob: Blob, name: string) {
  const uri = URL.createObjectURL(blob);
  try {
    const link = document.createElement("a");
    link.href = uri;
    link.download = name;
    link.click();
  } finally {
    setTimeout(() => URL.revokeObjectURL(uri), 1000);
  }
}

/** Expo's web picker creates a blob URL; revoking it never deletes the user's original. */
export function pickerTemporaryFile(uri: string) {
  return new TemporaryFile(async () => {
    if (uri.startsWith("blob:")) URL.revokeObjectURL(uri);
  });
}
