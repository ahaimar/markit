import type { ApiClient } from "@/lib/api/client";
import { API_BASE_URL } from "@/lib/api/client";
import { getAccessToken } from "@/lib/api/sessionStore";
import type {
  AuthResponse,
  Cart,
  ChangePasswordInput,
  CreateTaskInput,
  ListProductsParams,
  ListTasksParams,
  LoginInput,
  Order,
  PageResponse,
  PlaceOrderInput,
  Product,
  RefreshInput,
  RegisterInput,
  Task,
  UpdateProfileInput,
  UpdateTaskInput,
  User,
} from "@/lib/types";
import { ApiFailure } from "@/lib/types";

type Body = object | undefined;

interface ErrorBodyShape {
  code?: string;
  message?: string;
  details?: unknown;
  requestId?: string;
}

function toApiFailure(status: number, body: ErrorBodyShape | undefined, fallbackMessage: string): ApiFailure {
  if (!body) return new ApiFailure(status, undefined, fallbackMessage);
  return new ApiFailure(
    status,
    {
      code: body.code ?? "ERR_UNKNOWN",
      message: body.message ?? fallbackMessage,
      details: body.details != null ? (body.details as Record<string, unknown>) : null,
      requestId: body.requestId ?? null,
    },
    fallbackMessage,
  );
}

async function request<T>(
  path: string,
  init: { method?: string; body?: Body; userId?: string } = {},
): Promise<T> {
  const headers = new Headers();
  headers.set("Content-Type", "application/json");
  const token = getAccessToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (init.userId) headers.set("X-User-Id", init.userId);

  const res = await fetch(`${API_BASE_URL}${path}`, {
    method: init.method ?? "GET",
    headers,
    body: init.body ? JSON.stringify(init.body) : undefined,
  });

  if (!res.ok) {
    let body: ErrorBodyShape | undefined;
    try {
      body = await res.json();
    } catch {
      body = undefined;
    }
    throw toApiFailure(res.status, body, `Request failed with status ${res.status}`);
  }

  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

const qs = (params: ListProductsParams): string => {
  const search = new URLSearchParams();
  if (params.page !== undefined) search.set("page", String(params.page));
  if (params.pageSize !== undefined) search.set("pageSize", String(params.pageSize));
  if (params.category) search.set("category", params.category);
  if (params.search) search.set("search", params.search);
  const result = search.toString();
  return result ? `?${result}` : "";
};

export class HttpApiClient implements ApiClient {
  register(input: RegisterInput): Promise<AuthResponse> {
    return request<AuthResponse>("/api/users/register", { method: "POST", body: input });
  }

  login(input: LoginInput): Promise<AuthResponse> {
    return request<AuthResponse>("/api/users/login", { method: "POST", body: input });
  }

  refresh(input: RefreshInput): Promise<AuthResponse> {
    return request<AuthResponse>("/api/users/refresh", { method: "POST", body: input });
  }

  logout(refreshToken: string): Promise<void> {
    return request<void>("/api/users/logout", { method: "POST", body: { refreshToken } });
  }

  getMe(userId: string): Promise<User> {
    return request<User>(`/api/users/${userId}`, { userId });
  }

  updateProfile(userId: string, input: UpdateProfileInput): Promise<User> {
    return request<User>(`/api/users/${userId}`, { method: "PUT", body: input, userId });
  }

  changePassword(userId: string, input: ChangePasswordInput): Promise<void> {
    return request<void>("/api/users/change-password", { method: "POST", body: input, userId });
  }

  listProducts(params: ListProductsParams): Promise<PageResponse<Product>> {
    return request<PageResponse<Product>>(`/api/products${qs(params)}`);
  }

  getProduct(productId: string): Promise<Product> {
    return request<Product>(`/api/products/${productId}`);
  }

  getCart(userId: string): Promise<Cart> {
    return request<Cart>("/api/orders/cart", { userId });
  }

  addToCart(userId: string, productId: string, quantity: number): Promise<Cart> {
    return request<Cart>("/api/orders/cart/add", {
      method: "POST",
      body: { productId, quantity },
      userId,
    });
  }

  removeFromCart(userId: string, productId: string): Promise<Cart> {
    return request<Cart>("/api/orders/cart/remove", {
      method: "POST",
      body: { productId },
      userId,
    });
  }

  clearCart(userId: string): Promise<void> {
    return request<void>("/api/orders/cart/clear", { method: "POST", userId });
  }

  placeOrder(userId: string, input: PlaceOrderInput, idempotencyKey?: string): Promise<Order> {
    const headers = { body: input, userId };
    return customPost(`/api/orders`, headers, idempotencyKey);
  }

  listOrders(userId: string, page = 0, pageSize = 20): Promise<PageResponse<Order>> {
    return request<PageResponse<Order>>(`/api/orders?page=${page}&pageSize=${pageSize}`, {
      userId,
    });
  }

  getOrder(userId: string, orderId: string): Promise<Order> {
    return request<Order>(`/api/orders/${orderId}`, { userId });
  }

  listTasks(userId: string, params: ListTasksParams = {}): Promise<PageResponse<Task>> {
    const search = new URLSearchParams();
    if (params.page !== undefined) search.set("page", String(params.page));
    if (params.pageSize !== undefined) search.set("pageSize", String(params.pageSize));
    if (params.status) search.set("status", params.status);
    const query = search.toString();
    return request<PageResponse<Task>>(`/api/tasks${query ? `?${query}` : ""}`, { userId });
  }

  getTask(userId: string, taskId: string): Promise<Task> {
    return request<Task>(`/api/tasks/${taskId}`, { userId });
  }

  createTask(userId: string, input: CreateTaskInput): Promise<Task> {
    return request<Task>("/api/tasks", { method: "POST", body: input, userId });
  }

  updateTask(userId: string, taskId: string, input: UpdateTaskInput): Promise<Task> {
    return request<Task>(`/api/tasks/${taskId}`, { method: "PUT", body: input, userId });
  }

  deleteTask(userId: string, taskId: string): Promise<void> {
    return request<void>(`/api/tasks/${taskId}`, { method: "DELETE", userId });
  }
}

async function customPost<T>(
  path: string,
  init: { body: Body; userId?: string },
  idempotencyKey?: string,
): Promise<T> {
  const headers = new Headers();
  headers.set("Content-Type", "application/json");
  const token = getAccessToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (init.userId) headers.set("X-User-Id", init.userId);
  if (idempotencyKey) headers.set("Idempotency-Key", idempotencyKey);

  const res = await fetch(`${API_BASE_URL}${path}`, {
    method: "POST",
    headers,
    body: JSON.stringify(init.body),
  });

  if (!res.ok) {
    let body: ErrorBodyShape | undefined;
    try {
      body = await res.json();
    } catch {
      body = undefined;
    }
    throw toApiFailure(res.status, body, "Order could not be placed");
  }

  return (await res.json()) as T;
}

export function createHttpApiClient(): ApiClient {
  return new HttpApiClient();
}