import type { ApiClient } from "@/lib/api/client";
import { API_MODE } from "@/lib/api/client";
import { createHttpApiClient } from "@/lib/api/httpApiClient";
import { createMockApiClient } from "@/lib/api/mockApiClient";

let cached: ApiClient | null = null;

export function getApiClient(): ApiClient {
  if (!cached) {
    cached = API_MODE === "live" ? createHttpApiClient() : createMockApiClient();
  }
  return cached;
}