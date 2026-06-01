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
  const res = await fetch(
    `${API_BASE}/user/${encodeURIComponent(phoneNumber)}`,
    { cache: 'no-store' }
  );
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

export async function getAlert(phoneNumber: string): Promise<AlertResponse> {
  const res = await fetch(
    `${API_BASE}/alert/${encodeURIComponent(phoneNumber)}`,
    { cache: 'no-store' }
  );
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}
