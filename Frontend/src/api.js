const configuredApiUrl =
  import.meta.env.VITE_API_BASE_URL || import.meta.env.VITE_API_URL;
const API_BASE_URL = (
  configuredApiUrl ||
  (import.meta.env.PROD
    ? "https://shiptrack-backend-r684.onrender.com"
    : "http://localhost:8080")
).replace(/\/+$/, "");

export async function apiRequest(path, options = {}) {
  const token = localStorage.getItem("shiptrackToken");

  const headers = {
    "Content-Type": "application/json",
    ...(options.headers || {}),
  };

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  let response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers,
    });
  } catch (cause) {
    const error = new Error(
      `Could not reach the ShipTrack API at ${API_BASE_URL}. Check the API URL, deployment status, and CORS settings.`,
      { cause },
    );
    error.cause = cause;
    throw error;
  }

  const text = await response.text();
  let data;

  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text;
  }

  if (!response.ok) {
    if (response.status === 401) {
      localStorage.removeItem("shiptrackToken");
      localStorage.removeItem("shiptrackUser");
    }

    const message =
      (typeof data === "string" && data.trim()) ||
      data?.message ||
      data?.detail ||
      data?.error_description ||
      data?.error ||
      data?.title ||
      `Request failed with status ${response.status}`;

    const error = new Error(message);
    error.status = response.status;
    throw error;
  }

  return data;
}

export const API_BASE = API_BASE_URL;
