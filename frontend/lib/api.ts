const API_BASE = 'https://0tvnxmx6t7.execute-api.eu-west-2.amazonaws.com/Prod';

export interface AlertResponse {
  hasAlert: boolean;
  evacuationRoute?: string;
  message?: string;
  targetedBuilding?: string;
  distanceMeters?: number;
}

export interface UserResponse {
  registered: boolean;
  phoneNumber?: string;
  name?: string;
  lat?: number;
  lng?: number;
  emergencyContact?: string;
  peopleInHouse?: number;
}

export async function checkUser(phoneNumber: string): Promise<UserResponse> {
  const res = await fetch(`${API_BASE}/user/${phoneNumber}`, { cache: 'no-store' });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

export interface RegisterPayload {
  phoneNumber: string;
  name: string;
  lat: number | null;
  lng: number | null;
  emergencyContact: string | null;
  peopleInHouse: number;
}

export async function registerUser(payload: RegisterPayload): Promise<void> {
  const body = JSON.stringify(payload);
  console.log('[Salmak] POST /register body:', body);
  const res = await fetch(`${API_BASE}/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body,
  });
  if (!res.ok) {
    const text = await res.text();
    console.error('[Salmak] POST /register failed:', res.status, text);
    throw new Error(`HTTP ${res.status}`);
  }
}

export async function getAlert(phoneNumber: string): Promise<AlertResponse> {
  const url = `${API_BASE}/alert/${phoneNumber}`;
  console.log('[Salmak] GET', url);
  const res = await fetch(url, { cache: 'no-store' });
  console.log('[Salmak] status:', res.status);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}
