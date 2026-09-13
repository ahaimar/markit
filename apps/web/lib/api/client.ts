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

export interface ApiClient {
  register(input: RegisterInput): Promise<AuthResponse>;
  login(input: LoginInput): Promise<AuthResponse>;
  refresh(input: RefreshInput): Promise<AuthResponse>;
  logout(refreshToken: string): Promise<void>;
  getMe(userId: string): Promise<User>;
  updateProfile(userId: string, input: UpdateProfileInput): Promise<User>;
  changePassword(userId: string, input: ChangePasswordInput): Promise<void>;

  listProducts(params: ListProductsParams): Promise<PageResponse<Product>>;
  getProduct(productId: string): Promise<Product>;

  getCart(userId: string): Promise<Cart>;
  addToCart(userId: string, productId: string, quantity: number): Promise<Cart>;
  removeFromCart(userId: string, productId: string): Promise<Cart>;
  clearCart(userId: string): Promise<void>;

  placeOrder(
    userId: string,
    input: PlaceOrderInput,
    idempotencyKey?: string,
  ): Promise<Order>;
  listOrders(
    userId: string,
    page?: number,
    pageSize?: number,
  ): Promise<PageResponse<Order>>;
  getOrder(userId: string, orderId: string): Promise<Order>;

  listTasks(
    userId: string,
    params?: ListTasksParams,
  ): Promise<PageResponse<Task>>;
  getTask(userId: string, taskId: string): Promise<Task>;
  createTask(userId: string, input: CreateTaskInput): Promise<Task>;
  updateTask(userId: string, taskId: string, input: UpdateTaskInput): Promise<Task>;
  deleteTask(userId: string, taskId: string): Promise<void>;
}

export const API_MODE = (process.env.NEXT_PUBLIC_API_MODE ??
  "mock") as "mock" | "live";

export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";