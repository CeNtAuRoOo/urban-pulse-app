/** Releases only a picker-owned resource, once, after its last in-flight reader finishes. */
export class TemporaryFile {
  private readers = 0;
  private discarded = false;
  private released = false;
  constructor(private readonly release: () => Promise<void>) {}
  async use<T>(read: () => Promise<T>): Promise<T> {
    if (this.discarded) throw new Error("La fotografía ya no está seleccionada.");
    this.readers++;
    try {
      return await read();
    } finally {
      this.readers--;
      await this.flush();
    }
  }
  async dispose() {
    this.discarded = true;
    await this.flush();
  }
  private async flush() {
    if (!this.discarded || this.readers || this.released) return;
    this.released = true;
    try {
      await this.release();
    } catch {
      console.warn("No se pudo liberar una copia temporal de la fotografía.");
    }
  }
}

/** Expo SDK 57 copies picker results to cache/DocumentPicker/<generated UUID>.<extension>.
 * Only adopt that exact layout from a copyToCacheDirectory:true result; never scan a directory.
 */
export function isPickerCacheCopy(uri: string, cache: string | null): boolean {
  if (!cache?.startsWith("file://")) return false;
  const prefix = `${cache.replace(/\/$/, "")}/DocumentPicker/`;
  if (!uri.startsWith(prefix)) return false;
  const filename = uri.slice(prefix.length);
  return /^[a-f\d]{8}-[a-f\d]{4}-[a-f\d]{4}-[a-f\d]{4}-[a-f\d]{12}(?:\.[^/\\%?#]*)?$/i.test(filename);
}
