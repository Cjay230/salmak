const API_BASE = 'https://0tvnxmx6t7.execute-api.eu-west-2.amazonaws.com/Prod';

export interface AlertResponse {
  hasAlert: boolean;
  evacuationRoute?: string;
  message?: string;
  targetedBuilding?: string;
  distanceMeters?: number;
}

export interface RegisterResponse {
  success?: boolean;
  isNewUser?: boolean;
  message?: string;
  token?: string;
}

export async function sendVerificationCode(phoneNumber: string): Promise<RegisterResponse> {
  const res = await fetch(`${API_BASE}/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ phoneNumber }),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

export async function verifyCode(phoneNumber: string, code: string): Promise<RegisterResponse> {
  const res = await fetch(`${API_BASE}/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ phoneNumber, code }),
  });
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
