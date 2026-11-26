import type { MapFeature } from "../types/operations";
export interface Viewport {
  minLat: number;
  minLon: number;
  maxLat: number;
  maxLon: number;
  zoom: number;
}
export interface GeoMapProps {
  items: MapFeature[];
  onViewport?: (v: Viewport) => void;
  onSelect?: (item: MapFeature) => void;
  onPick?: (coords: { latitude: number; longitude: number }) => void;
  focus?: { latitude: number; longitude: number; zoom: number };
}
export function normalizeViewport(v: Viewport): Viewport {
  const span = Math.max(v.maxLat - v.minLat, v.maxLon - v.minLon);
  return {
    ...v,
    zoom: Math.max(
      0,
      Math.min(22, Math.floor(v.zoom), Math.floor(Math.log2(1440 / span))),
    ),
  };
}
export const initialViewport: Viewport = {
  minLat: 36.25,
  minLon: -5.65,
  maxLat: 37.35,
  maxLon: -3.65,
  zoom: 8,
};
