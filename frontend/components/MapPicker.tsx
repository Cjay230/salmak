'use client';

import { useEffect, useRef } from 'react';
import { MapContainer, TileLayer, Marker, useMap, useMapEvents } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

const customIcon = L.icon({
  iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
  iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
  shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41],
});

// Flies the map to a new position whenever it changes, skipping identical coords.
function MapController({ position }: { position: [number, number] | null }) {
  const map = useMap();
  const prevKey = useRef('');

  useEffect(() => {
    if (!position) return;
    const key = `${position[0].toFixed(6)},${position[1].toFixed(6)}`;
    if (key === prevKey.current) return;
    prevKey.current = key;
    map.flyTo(position, Math.max(map.getZoom(), 15), { duration: 1 });
  }, [position, map]);

  return null;
}

function ClickHandler({ onSelect }: { onSelect: (lat: number, lng: number) => void }) {
  useMapEvents({
    click(e) {
      onSelect(e.latlng.lat, e.latlng.lng);
    },
  });
  return null;
}

export interface MapLocation {
  lat: number;
  lng: number;
}

interface MapPickerProps {
  location: MapLocation | null;
  onLocationSelect: (lat: number, lng: number) => void;
}

export default function MapPicker({ location, onLocationSelect }: MapPickerProps) {
  const markerPos: [number, number] | null = location
    ? [location.lat, location.lng]
    : null;

  return (
    <MapContainer
      center={[33.8938, 35.5018]}
      zoom={13}
      style={{ height: '250px', width: '100%' }}
      scrollWheelZoom={false}
    >
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <MapController position={markerPos} />
      <ClickHandler onSelect={onLocationSelect} />
      {markerPos && <Marker position={markerPos} icon={customIcon} />}
    </MapContainer>
  );
}
