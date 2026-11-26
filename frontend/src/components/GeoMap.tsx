import { useCallback, useEffect, useId, useMemo, useRef } from "react";
import { View } from "react-native";
import { WebView } from "react-native-webview";
import { mapDocument } from "../maps/document";
import { normalizeViewport, type GeoMapProps } from "../maps/types";
export function GeoMap(props: GeoMapProps) {
  const id = useId(),
    view = useRef<WebView>(null),
    timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined),
    current = useRef(props);
  useEffect(() => {
    current.current = props;
  }, [props]);
  const picking = !!props.onPick;
  const html = useMemo(() => mapDocument(id, picking), [id, picking]);
  const post = useCallback(
    (type: string, payload: unknown) =>
      view.current?.postMessage(JSON.stringify({ channel: id, type, payload })),
    [id]
  );
  useEffect(() => {
    post("items", props.items);
  }, [props.items, post]);
  useEffect(() => {
    if (props.focus) post("focus", props.focus);
  }, [props.focus, post]);
  useEffect(() => () => clearTimeout(timer.current), []);
  return (
    <View style={{ height: 480, borderRadius: 12, overflow: "hidden" }}>
      <WebView
        ref={view}
        source={{ html }}
        originWhitelist={["about:blank"]}
        javaScriptEnabled
        userAgent="UrbanPulse/1.0 (interactive municipal map)"
        onShouldStartLoadWithRequest={(r) => r.url === "about:blank"}
        onMessage={(event) => {
          let d;
          try {
            d = JSON.parse(event.nativeEvent.data);
          } catch {
            return;
          }
          if (d.channel !== id) return;
          if (d.type === "ready") {
            post("items", current.current.items);
            if (current.current.focus) post("focus", current.current.focus);
          }
          if (d.type === "viewport") {
            clearTimeout(timer.current);
            timer.current = setTimeout(() => current.current.onViewport?.(normalizeViewport(d.payload)), 400);
          }
          if (d.type === "select") {
            const item = current.current.items.find(
              (i) => (i.type === "CLUSTER" ? i.clusterId : i.incidentId) === d.payload
            );
            if (item) current.current.onSelect?.(item);
          }
          if (d.type === "pick") current.current.onPick?.(d.payload);
        }}
      />
    </View>
  );
}
