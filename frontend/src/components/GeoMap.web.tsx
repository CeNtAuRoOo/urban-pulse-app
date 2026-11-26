import { useCallback, useEffect, useId, useMemo, useRef } from "react";
import { mapDocument } from "../maps/document";
import { normalizeViewport, type GeoMapProps } from "../maps/types";
export function GeoMap(props: GeoMapProps) {
  const id = useId(),
    frame = useRef<HTMLIFrameElement>(null),
    current = useRef(props);
  useEffect(() => {
    current.current = props;
  }, [props]);
  const picking = !!props.onPick;
  const document = useMemo(() => mapDocument(id, picking), [id, picking]);
  const post = useCallback(
    (type: string, payload: unknown) =>
      frame.current?.contentWindow?.postMessage({ channel: id, type, payload }, "*"),
    [id]
  );
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>;
    const receive = (event: MessageEvent) => {
      if (event.source !== frame.current?.contentWindow || event.data?.channel !== id) return;
      const { type, payload } = event.data;
      if (type === "ready") {
        post("items", current.current.items);
        if (current.current.focus) post("focus", current.current.focus);
      }
      if (type === "viewport") {
        clearTimeout(timer);
        timer = setTimeout(() => current.current.onViewport?.(normalizeViewport(payload)), 400);
      }
      if (type === "select") {
        const item = current.current.items.find(
          (i) => (i.type === "CLUSTER" ? i.clusterId : i.incidentId) === payload
        );
        if (item) current.current.onSelect?.(item);
      }
      if (type === "pick") current.current.onPick?.(payload);
    };
    window.addEventListener("message", receive);
    return () => {
      window.removeEventListener("message", receive);
      clearTimeout(timer);
    };
  }, [id, post]);
  useEffect(() => post("items", props.items), [props.items, post]);
  useEffect(() => {
    if (props.focus) post("focus", props.focus);
  }, [props.focus, post]);
  return (
    // A srcDoc sandbox without allow-same-origin suppresses the tile Referer in Chromium.
    // OSM requires it: https://operations.osmfoundation.org/policies/tiles/
    // Keep the existing origin until a separately hosted map document is available.
    // Do not combine allow-scripts + allow-same-origin as a supposed isolation measure.
    <iframe
      ref={frame}
      title="Mapa interactivo de Málaga"
      srcDoc={document}
      referrerPolicy="strict-origin-when-cross-origin"
      style={{
        width: "100%",
        height: 480,
        border: "1px solid #D5E0E5",
        borderRadius: 12,
      }}
    />
  );
}
