export interface Product {
  id: string;
  name: string;
  description: string;
  price: number;
  category: string;
  stockQuantity: number;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
}

export interface User {
  id: string;
  email: string;
  name: string;
  address: string | null;
  role: string;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: User;
}

export interface CartItem {
  productId: string;
  productName: string;
  unitPrice: number;
  quantity: number;
  totalPrice: number;
}

export interface Cart {
  cartId: string | null;
  userId: string;
  items: CartItem[];
  totalPrice: number;
}

export interface OrderItem {
  productId: string;
  productName: string;
  quantity: number;
  priceAtPurchase: number;
}

export type OrderStatus =
  | "PENDING"
  | "CONFIRMED"
  | "SHIPPED"
  | "DELIVERED"
  | "CANCELLED";

export type TaskStatus = "TODO" | "IN_PROGRESS" | "DONE";

export type TaskPriority = "LOW" | "MEDIUM" | "HIGH";

export interface Task {
  taskId: string;
  userId: string;
  title: string;
  description: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  dueDate: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Order {
  orderId: string;
  userId: string;
  status: OrderStatus;
  totalPrice: number;
  shippingAddress: string | null;
  createdAt: string;
  items: OrderItem[];
}

export interface ApiErrorBody {
  code: string;
  message: string;
  details?: Record<string, unknown> | null;
  requestId?: string | null;
}

export class ApiFailure extends Error {
  readonly code: string;
  readonly details: Record<string, unknown> | null;
  readonly requestId: string | null;
  readonly status: number;

  constructor(
    status: number,
    body: ApiErrorBody | undefined,
    fallbackMessage = "Request failed",
  ) {
    super(body?.message ?? fallbackMessage);
    this.name = "ApiFailure";
    this.status = status;
    this.code = body?.code ?? "ERR_UNKNOWN";
    this.details = body?.details ?? null;
    this.requestId = body?.requestId ?? null;
  }
}

export interface ListProductsParams {
  page?: number;
  pageSize?: number;
  category?: string;
  search?: string;
}

export interface ListTasksParams {
  page?: number;
  pageSize?: number;
  status?: TaskStatus;
}

export interface CreateTaskInput {
  title: string;
  description?: string;
  status?: TaskStatus;
  priority?: TaskPriority;
  dueDate?: string;
}

export interface UpdateTaskInput {
  title?: string;
  description?: string;
  status?: TaskStatus;
  priority?: TaskPriority;
  dueDate?: string;
}

export interface RegisterInput {
  email: string;
  name: string;
  password: string;
}

export interface LoginInput {
  email: string;
  password: string;
}

export interface RefreshInput {
  refreshToken: string;
}

export interface UpdateProfileInput {
  email?: string;
  name?: string;
  address?: string;
}

export interface ChangePasswordInput {
  currentPassword: string;
  newPassword: string;
}

export interface PlaceOrderInput {
  cartId: string;
  shippingAddress: string;
}

export interface Session {
  accessToken: string;
  refreshToken: string;
  user: User;
}