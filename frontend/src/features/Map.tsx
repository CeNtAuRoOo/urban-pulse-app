import { useCallback, useState } from "react";
import { useLocalSearchParams, useRouter } from "expo-router";
import { Pressable, ScrollView, Text, View, useWindowDimensions } from "react-native";
import Feather from "@expo/vector-icons/Feather";
import {
  Badge,
  Body,
  Button,
  NavLink,
  PageHeading,
  Panel,
  QueryState,
  Row,
  Select,
  options,
  statusNames,
  priorities,
  date,
  styles,
} from "../components/ui";
import { GeoMap } from "../components/GeoMap";
import { Disclosure } from "../components/Disclosure";
import { useResource } from "../services/hooks";
import { incidentHref, routeKey } from "../services/navigation";
import { colors as c } from "../design/tokens";
import type { Category } from "../types/api";
import type { MapFeature, MapResult } from "../types/operations";
import { initialViewport, type Viewport } from "../maps/types";
const empty: MapFeature[] = [];
function readViewport(params: Record<string, string>): Viewport {
  const candidate = Object.fromEntries(
    Object.entries(initialViewport).map(([key, fallback]) => [
      key,
      params[key] !== undefined ? Number(params[key]) : fallback,
    ])
  ) as unknown as Viewport;
  return Object.values(candidate).every(Number.isFinite) &&
    candidate.minLat < candidate.maxLat &&
    candidate.minLon < candidate.maxLon &&
    candidate.zoom >= 0 &&
    candidate.zoom <= 22
    ? candidate
    : initialViewport;
}
export function MapScreen() {
  const params = useLocalSearchParams<Record<string, string>>(),
    router = useRouter(),
    mobile = useWindowDimensions().width < 900;
  const [viewport, setViewport] = useState(() => readViewport(params)),
    [shown, setShown] = useState(25);
  const status = params.status || "",
    categoryId = params.categoryId || "";
  const [focus, setFocus] = useState(() => ({
    latitude: (viewport.minLat + viewport.maxLat) / 2,
    longitude: (viewport.minLon + viewport.maxLon) / 2,
    zoom: viewport.zoom,
  }));
  const map = useResource<MapResult>("/map/incidents", { ...viewport, status, categoryId }),
    categories = useResource<Category[]>("/categories");
  const update = useCallback(
    (v: Viewport) => {
      setViewport(v);
      setShown(25);
      router.setParams(Object.fromEntries(Object.entries(v).map(([k, n]) => [k, String(n)])));
    },
    [router]
  );
  const items = map.result?.data.items || empty,
    selected = items.find((i) => i.type === "INCIDENT" && i.incidentId === params.selected);
  const select = (item: MapFeature) => {
    if (item.type === "CLUSTER") {
      setFocus({ ...item.coordinates, zoom: Math.min(22, viewport.zoom + 2) });
      router.setParams({ selected: "" });
    } else router.setParams({ selected: item.incidentId });
  };
  return (
    <>
      <PageHeading
        title="La ciudad, en el mapa."
        description="Explora las incidencias de la provincia de Málaga y descubre qué ocurre cerca."
      />
      <Panel>
        <Row>
          <View style={{ flex: 1, minWidth: 180 }}>
            <Select
              label="Estado"
              value={status}
              onChange={(value) => router.setParams({ status: value, selected: "" })}
              options={options(
                Object.fromEntries(
                  Object.entries(statusNames).filter(
                    ([k]) => !["ACTIVE", "REDEEMED", "REVOKED", "EXPIRED"].includes(k)
                  )
                ),
                "Todos"
              )}
            />
          </View>
          <View style={{ flex: 1, minWidth: 180 }}>
            <Select
              label="Categoría"
              value={categoryId}
              onChange={(value) => router.setParams({ categoryId: value, selected: "" })}
              options={[
                { value: "", label: "Todas" },
                ...(categories.result?.data || []).map((cat) => ({ value: cat.id, label: cat.name })),
              ]}
            />
          </View>
        </Row>
        <View style={{ position: "relative" }}>
          <GeoMap items={items} onViewport={update} onSelect={select} focus={focus} />
          {selected?.type === "INCIDENT" ? (
            <View
              testID="map-context-panel"
              accessibilityLabel="Incidencia seleccionada"
              style={{
                position: "absolute",
                zIndex: 10,
                ...(mobile
                  ? { left: 10, right: 10, bottom: 30, maxHeight: 300 }
                  : { right: 18, top: 18, width: 330, maxHeight: 420 }),
                backgroundColor: c.surface,
                borderRadius: 20,
                borderWidth: 1,
                borderColor: c.border,
                boxShadow: "0 8px 28px #172C4226",
              }}
            >
              <ScrollView contentContainerStyle={{ padding: 20, gap: 14 }}>
                <View style={[styles.row, { alignItems: "flex-start" }]}>
                  <View style={{ flex: 1, gap: 8 }}>
                    <Badge tone={selected.status === "REJECTED" ? "danger" : "normal"}>
                      {statusNames[selected.status]}
                    </Badge>
                    <Text role="heading" aria-level={2} style={[styles.h2, { fontSize: 18, lineHeight: 25 }]}>
                      {selected.title}
                    </Text>
                  </View>
                  <Pressable
                    accessibilityRole="button"
                    accessibilityLabel="Cerrar ficha del mapa"
                    onPress={() => router.setParams({ selected: "" })}
                    style={{
                      padding: 10,
                      minWidth: 44,
                      minHeight: 44,
                      backgroundColor: c.canvas,
                      borderRadius: 22,
                    }}
                  >
                    <Feather name="x" size={20} color={c.ink} />
                  </Pressable>
                </View>
                <Body muted>
                  {selected.category?.name || "Sin categoría"}
                  {selected.priority ? ` · Prioridad ${priorities[selected.priority]}` : ""}
                </Body>
                <Body muted>
                  {selected.coordinatePrecision === "EXACT" ? "Ubicación exacta" : "Ubicación aproximada"} ·{" "}
                  {date(selected.createdAt)}
                </Body>
                {selected.canAccessPrivateDetail ? (
                  <NavLink href={incidentHref(selected.incidentId, routeKey("/map", params))} button>
                    Abrir expediente →
                  </NavLink>
                ) : (
                  <Body muted>Este marcador solo dispone de información pública.</Body>
                )}
              </ScrollView>
            </View>
          ) : null}
        </View>
        <QueryState {...map} empty={map.result?.data.count === 0} />
        {map.error ? (
          <Body muted>Si hay demasiados elementos, reduce el área o aleja el mapa para agruparlos.</Body>
        ) : null}
        <Body muted>
          {map.result
            ? `${map.result.data.totalIncidents.toLocaleString("es-ES")} incidencias en el área. `
            : ""}
          Las posiciones ajenas pueden ser aproximadas. Cartografía © OpenStreetMap contributors.
        </Body>
        <NavLink href="/urban-assets">Consultar activos urbanos →</NavLink>
        <Disclosure title="Explorar los resultados en una lista">
          <Body muted>
            Selecciona una incidencia para abrir su ficha sobre el mapa o un grupo para acercarte.
          </Body>
          {items.slice(0, shown).map((item) => (
            <View key={item.type === "CLUSTER" ? item.clusterId : item.incidentId} style={styles.divider}>
              <Button secondary onPress={() => select(item)}>
                {item.type === "CLUSTER" ? `Acercar grupo de ${item.incidentCount} incidencias` : item.title}
              </Button>
            </View>
          ))}
          {shown < items.length ? (
            <Button secondary onPress={() => setShown((v) => v + 25)}>
              Mostrar más elementos
            </Button>
          ) : null}
        </Disclosure>
      </Panel>
    </>
  );
}
