/** One registry for all forms: scoped cancellation must not discard neighbouring drafts. */
export class UnsavedChanges {
  private forms = new Set<string>();
  update(id: string, dirty: boolean) {
    if (dirty) this.forms.add(id);
    else this.forms.delete(id);
  }
  has(id?: string) {
    return id ? this.forms.has(id) : this.forms.size > 0;
  }
  hasOther(id: string) {
    return [...this.forms].some((form) => form !== id);
  }
  clear(id?: string) {
    if (id) this.forms.delete(id);
    else this.forms.clear();
  }
}
