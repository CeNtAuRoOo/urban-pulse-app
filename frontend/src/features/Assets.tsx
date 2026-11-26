import { useState } from "react";
import {
  Body,
  Button,
  Details,
  Feedback,
  Field,
  PageHeading,
  Panel,
  QueryState,
  Row,
} from "../components/ui";
import { useResource } from "../services/hooks";
import type { Query } from "../services/http";
import type { UrbanAsset } from "../types/operations";
export function Assets() {
  const [type, setType] = useState(""),
    [latitude, setLatitude] = useState(""),
    [longitude, setLongitude] = useState(""),
    [radius, setRadius] = useState(""),
    [query, setQuery] = useState<Query>({}),
    [error, setError] = useState<unknown>(),
    [shown, setShown] = useState(20),
    [selected, setSelected] = useState<number>();
  const list = useResource<UrbanAsset[]>("/urban-assets", query);
  return (
    <>
      <PageHeading
        title="Activos urbanos"
        description="Consulta el catálogo territorial y encuentra elementos próximos a una ubicación."
      />
      <Panel>
        <Field
          label="Tipo de origen (coincidencia exacta)"
          value={type}
          onChangeText={setType}
          maxLength={64}
        />
        <Row>
          <Field
            label="Latitud"
            value={latitude}
            onChangeText={setLatitude}
            keyboardType="numbers-and-punctuation"
          />
          <Field
            label="Longitud"
            value={longitude}
            onChangeText={setLongitude}
            keyboardType="numbers-and-punctuation"
          />
          <Field label="Radio en metros" value={radius} onChangeText={setRadius} keyboardType="number-pad" />
        </Row>
        <Feedback error={error} />
        <Button
          onPress={() => {
            try {
              const next: Query = { type: type.trim() };
              if (latitude || longitude || radius) {
                const lat = Number(latitude),
                  lon = Number(longitude),
                  r = Number(radius);
                if (
                  !latitude ||
                  !longitude ||
                  !radius ||
                  !Number.isFinite(lat) ||
                  !Number.isFinite(lon) ||
                  !Number.isFinite(r) ||
                  lat < -90 ||
                  lat > 90 ||
                  lon < -180 ||
                  lon > 180 ||
                  r < 1 ||
                  r > 5000
                )
                  throw new Error("Completa latitud, longitud y un radio de 1 a 5000 metros.");
                Object.assign(next, {
                  latitude: lat,
                  longitude: lon,
                  radiusMeters: r,
                });
              }
              setError(undefined);
              setQuery(next);
              setShown(20);
              setSelected(undefined);
            } catch (e) {
              setError(e);
            }
          }}
        >
          Buscar activos
        </Button>
        <QueryState {...list} empty={list.result?.data.length === 0} />
        {list.result ? (
          <>
            <Body muted>
              {list.result.data.length} activos devueltos. La consulta está limitada a 500 resultados; utiliza
              la búsqueda por proximidad para acotar.
            </Body>
            {list.result.data.slice(0, shown).map((a) => (
              <Panel key={a.id} title={a.name || `Activo ${a.id}`}>
                <Body muted>
                  {a.sourceTable}
                  {a.category ? ` · ${a.category}` : ""}
                </Body>
                {a.description ? <Body>{a.description}</Body> : null}
                <Button secondary onPress={() => setSelected(selected === a.id ? undefined : a.id)}>
                  {selected === a.id ? "Ocultar ficha" : "Ver ficha"}
                </Button>
                {selected === a.id ? (
                  <Details
                    entries={[
                      ["Identificador", String(a.id)],
                      ["Origen", a.sourceTable],
                      ["Geometría (WKT, EPSG:25830)", a.geom],
                    ]}
                  />
                ) : null}
              </Panel>
            ))}
            {shown < list.result.data.length ? (
              <Button secondary onPress={() => setShown((v) => v + 20)}>
                Mostrar más activos
              </Button>
            ) : null}
          </>
        ) : null}
      </Panel>
    </>
  );
}
