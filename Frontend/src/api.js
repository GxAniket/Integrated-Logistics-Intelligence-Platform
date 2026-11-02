const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

export async function apiRequest(path, options = {}) {
  const token = localStorage.getItem("shiptrackToken");

  const headers = {
    "Content-Type": "application/json",
    ...(options.headers || {}),
  };

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
  });

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
      data?.message ||
      data?.error ||
      data ||
      `Request failed with status ${response.status}`;

    const error = new Error(message);
    error.status = response.status;
    throw error;
  }

  return data;
}

export const API_BASE = API_BASE_URL;