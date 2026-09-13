import type { ApiClient } from "@/lib/api/client";
import type {
  AuthResponse,
  Cart,
  CartItem,
  ChangePasswordInput,
  CreateTaskInput,
  ListProductsParams,
  ListTasksParams,
  LoginInput,
  Order,
  OrderItem,
  PageResponse,
  PlaceOrderInput,
  Product,
  RefreshInput,
  RegisterInput,
  Task,
  TaskPriority,
  TaskStatus,
  UpdateProfileInput,
  UpdateTaskInput,
  User,
} from "@/lib/types";
import { ApiFailure } from "@/lib/types";

interface StoredUser extends User {
  password: string;
}

const SEED_PRODUCTS: Product[] = [
  { id: "1c7e3c9a-6c5e-4e49-9a8a-3f4b2f8e5a01", name: "Coffee Beans - Ethiopia", description: "Single origin, medium roast.", price: 100.0, category: "Coffee", stockQuantity: 40 },
  { id: "2d8f4dab-7d6f-4f5a-9b9b-4g5c3g9f6b02", name: "Ceramic Mug", description: "Hand-thrown stoneware mug, 350ml.", price: 18.5, category: "Mugs", stockQuantity: 25 },
  { id: "3e9g5ebc-8e7g-5g6b-9c9c-5h6d4h0g7c03", name: "Pour-Over Kettle", description: "Gooseneck kettle with thermometer.", price: 65.0, category: "Brewing", stockQuantity: 12 },
  { id: "4f0h6fcd-9f8h-6h7c-9d9d-6i7e5i1h8d04", name: "Paper Filters x100", description: "V60 compatible filters.", price: 7.99, category: "Brewing", stockQuantity: 80 },
  { id: "5g1i7gde-0g9i-7i8d-9e9e-7j8f6j2i9e05", name: "Coffee Grinder", description: "Burr grinder, 30 grind settings.", price: 89.0, category: "Brewing", stockQuantity: 8 },
  { id: "6h2j8hef-1h0j-8j9e-9f9f-8k9g7k3j0f06", name: "Espresso Cups (Set of 2)", description: "Double-wall glass espresso cups.", price: 24.0, category: "Mugs", stockQuantity: 15 },
];

const FAILURE = (status: number, code: string, message: string): never => {
  throw new ApiFailure(status, { code, message });
};

const ERRORS = {
  EMAIL_TAKEN: () => FAILURE(409, "ERR_EMAIL_TAKEN", "A user with this email already exists"),
  INVALID_CREDENTIALS: () => FAILURE(401, "ERR_INVALID_CREDENTIALS", "Invalid email or password"),
  UNAUTHORIZED: () => FAILURE(401, "ERR_UNAUTHORIZED", "Authentication required"),
  NOT_FOUND: (what: string) => FAILURE(404, "ERR_NOT_FOUND", `${what} not found`),
  FORBIDDEN: () => FAILURE(403, "ERR_FORBIDDEN", "Access denied"),
  INSUFFICIENT_STOCK: (name: string) =>
    FAILURE(409, "ERR_INSUFFICIENT_STOCK", `Insufficient stock for ${name}`),
};

const delay = (ms = 250) => new Promise((resolve) => setTimeout(resolve, ms));
const nowIso = () => new Date().toISOString();

export class MockApiClient implements ApiClient {
  private readonly products: Product[] = SEED_PRODUCTS.map((p) => ({ ...p }));
  private readonly usersById = new Map<string, StoredUser>();
  private readonly usersByEmail = new Map<string, StoredUser>();
  private readonly refreshTokens = new Map<string, string>();
  private readonly carts = new Map<string, Cart>();
  private readonly orders = new Map<string, Order>();
  private readonly tasks = new Map<string, Task>();

  private static token(userId: string, kind: "access" | "refresh"): string {
    return `mock.${kind}.${userId}.${Math.random().toString(36).slice(2)}`;
  }

  private newSession(user: User): AuthResponse {
    const session: AuthResponse = {
      accessToken: MockApiClient.token(user.id, "access"),
      refreshToken: MockApiClient.token(user.id, "refresh"),
      user,
    };
    this.refreshTokens.set(session.refreshToken, user.id);
    return session;
  }

  private getUser(userId: string): StoredUser {
    const user = this.usersById.get(userId);
    if (!user) throw ERRORS.NOT_FOUND("User");
    return user;
  }

  private publicUser(user: StoredUser): User {
    return {
      id: user.id,
      email: user.email,
      name: user.name,
      address: user.address,
      role: user.role,
      createdAt: user.createdAt,
    };
  }

  private cartFor(userId: string): Cart {
    let cart = this.carts.get(userId);
    if (!cart) {
      cart = { cartId: crypto.randomUUID(), userId, items: [], totalPrice: 0 };
      this.carts.set(userId, cart);
    }
    return cart;
  }

  private recomputeTotal(cart: Cart): Cart {
    cart.totalPrice = cart.items.reduce((sum, item) => sum + item.totalPrice, 0);
    return cart;
  }

  async register(input: RegisterInput): Promise<AuthResponse> {
    await delay();
    const email = input.email.toLowerCase();
    if (this.usersByEmail.has(email)) throw ERRORS.EMAIL_TAKEN();
    const user: StoredUser = {
      id: crypto.randomUUID(),
      email: input.email,
      name: input.name,
      address: null,
      role: "CUSTOMER",
      createdAt: nowIso(),
      password: input.password,
    };
    this.usersById.set(user.id, user);
    this.usersByEmail.set(email, user);
    this.cartFor(user.id);
    return this.newSession(this.publicUser(user));
  }

  async login(input: LoginInput): Promise<AuthResponse> {
    await delay();
    const user = this.usersByEmail.get(input.email.toLowerCase());
    if (!user || user.password !== input.password) throw ERRORS.INVALID_CREDENTIALS();
    return this.newSession(this.publicUser(user));
  }

  async refresh(input: RefreshInput): Promise<AuthResponse> {
    await delay(150);
    const userId = this.refreshTokens.get(input.refreshToken);
    if (!userId) throw ERRORS.UNAUTHORIZED();
    return this.newSession(this.publicUser(this.getUser(userId)));
  }

  async logout(refreshToken: string): Promise<void> {
    await delay(100);
    this.refreshTokens.delete(refreshToken);
  }

  async getMe(userId: string): Promise<User> {
    await delay(150);
    return this.publicUser(this.getUser(userId));
  }

  async updateProfile(userId: string, input: UpdateProfileInput): Promise<User> {
    await delay(200);
    const user = this.getUser(userId);
    if (input.email !== undefined) {
      const next = input.email.toLowerCase();
      const existing = this.usersByEmail.get(next);
      if (existing && existing.id !== userId) throw ERRORS.EMAIL_TAKEN();
      this.usersByEmail.delete(user.email.toLowerCase());
      user.email = input.email;
      this.usersByEmail.set(next, user);
    }
    if (input.name !== undefined) user.name = input.name;
    if (input.address !== undefined) user.address = input.address;
    return this.publicUser(user);
  }

  async changePassword(userId: string, input: ChangePasswordInput): Promise<void> {
    await delay(200);
    const user = this.getUser(userId);
    if (user.password !== input.currentPassword) throw ERRORS.INVALID_CREDENTIALS();
    user.password = input.newPassword;
  }

  async listProducts(params: ListProductsParams): Promise<PageResponse<Product>> {
    await delay();
    const page = params.page ?? 0;
    const pageSize = params.pageSize ?? 20;
    let items = this.products;
    if (params.category) {
      items = items.filter((p) => p.category.toLowerCase() === params.category!.toLowerCase());
    }
    if (params.search) {
      const q = params.search.toLowerCase();
      items = items.filter(
        (p) =>
          p.name.toLowerCase().includes(q) ||
          p.description.toLowerCase().includes(q) ||
          p.category.toLowerCase().includes(q),
      );
    }
    const start = page * pageSize;
    return {
      items: items.slice(start, start + pageSize),
      page,
      pageSize,
      total: items.length,
    };
  }

  async getProduct(productId: string): Promise<Product> {
    await delay(120);
    const product = this.products.find((p) => p.id === productId);
    if (!product) throw ERRORS.NOT_FOUND("Product");
    return product;
  }

  async getCart(userId: string): Promise<Cart> {
    await delay(150);
    return this.recomputeTotal(this.cartFor(userId));
  }

  async addToCart(userId: string, productId: string, quantity: number): Promise<Cart> {
    await delay();
    const product = this.products.find((p) => p.id === productId);
    if (!product) throw ERRORS.NOT_FOUND("Product");
    if (product.stockQuantity < quantity) throw ERRORS.INSUFFICIENT_STOCK(product.name);
    const cart = this.cartFor(userId);
    const existing = cart.items.find((item) => item.productId === productId);
    if (existing) {
      if (product.stockQuantity < existing.quantity + quantity) throw ERRORS.INSUFFICIENT_STOCK(product.name);
      existing.quantity += quantity;
      existing.totalPrice = existing.unitPrice * existing.quantity;
    } else {
      const item: CartItem = {
        productId,
        productName: product.name,
        unitPrice: product.price,
        quantity,
        totalPrice: product.price * quantity,
      };
      cart.items.push(item);
    }
    return this.recomputeTotal(cart);
  }

  async removeFromCart(userId: string, productId: string): Promise<Cart> {
    await delay(150);
    const cart = this.cartFor(userId);
    cart.items = cart.items.filter((item) => item.productId !== productId);
    return this.recomputeTotal(cart);
  }

  async clearCart(userId: string): Promise<void> {
    await delay(150);
    const cart = this.cartFor(userId);
    cart.items = [];
    this.recomputeTotal(cart);
  }

  private orderFromCart(userId: string, cart: Cart, shippingAddress: string): Order {
    const items: OrderItem[] = cart.items.map((item) => ({
      productId: item.productId,
      productName: item.productName,
      quantity: item.quantity,
      priceAtPurchase: item.unitPrice,
    }));
    return {
      orderId: crypto.randomUUID(),
      userId,
      status: "PENDING",
      totalPrice: cart.totalPrice,
      shippingAddress,
      createdAt: nowIso(),
      items,
    };
  }

  async placeOrder(userId: string, input: PlaceOrderInput): Promise<Order> {
    await delay(400);
    const cart = this.cartFor(userId);
    if (cart.items.length === 0) FAILURE(400, "ERR_EMPTY_CART", "Your cart is empty");
    for (const item of cart.items) {
      const product = this.products.find((p) => p.id === item.productId);
      if (!product) throw ERRORS.NOT_FOUND("Product");
      if (product.stockQuantity < item.quantity) throw ERRORS.INSUFFICIENT_STOCK(product.name);
    }
    const order = this.orderFromCart(userId, cart, input.shippingAddress);
    for (const item of cart.items) {
      const product = this.products.find((p) => p.id === item.productId)!;
      product.stockQuantity -= item.quantity;
    }
    this.orders.set(order.orderId, order);
    cart.items = [];
    this.recomputeTotal(cart);
    return order;
  }

  async listOrders(userId: string, page = 0, pageSize = 20): Promise<PageResponse<Order>> {
    await delay();
    const items = [...this.orders.values()]
      .filter((o) => o.userId === userId)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    const start = page * pageSize;
    return { items: items.slice(start, start + pageSize), page, pageSize, total: items.length };
  }

  async getOrder(userId: string, orderId: string): Promise<Order> {
    await delay(150);
    const order = this.orders.get(orderId);
    if (!order) throw ERRORS.NOT_FOUND("Order");
    if (order.userId !== userId) throw ERRORS.FORBIDDEN();
    return order;
  }

  private assertTaskStatus(status: string): asserts status is TaskStatus {
    if (status !== "TODO" && status !== "IN_PROGRESS" && status !== "DONE") {
      FAILURE(400, "ERR_INVALID_STATUS", `Invalid status value: ${status}`);
    }
  }

  private assertTaskPriority(priority: string): asserts priority is TaskPriority {
    if (priority !== "LOW" && priority !== "MEDIUM" && priority !== "HIGH") {
      FAILURE(400, "ERR_INVALID_PRIORITY", `Invalid priority value: ${priority}`);
    }
  }

  private toTask(userId: string, input: CreateTaskInput): Task {
    const status = input.status ?? "TODO";
    const priority = input.priority ?? "MEDIUM";
    this.assertTaskStatus(status);
    this.assertTaskPriority(priority);
    return {
      taskId: crypto.randomUUID(),
      userId,
      title: input.title,
      description: input.description ?? null,
      status,
      priority,
      dueDate: input.dueDate ?? null,
      createdAt: nowIso(),
      updatedAt: nowIso(),
    };
  }

  async listTasks(userId: string, params: ListTasksParams = {}): Promise<PageResponse<Task>> {
    await delay();
    const page = params.page ?? 0;
    const pageSize = params.pageSize ?? 20;
    let items = [...this.tasks.values()]
      .filter((t) => t.userId === userId)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    if (params.status) {
      const status = params.status;
      this.assertTaskStatus(status);
      items = items.filter((t) => t.status === status);
    }
    const start = page * pageSize;
    return { items: items.slice(start, start + pageSize), page, pageSize, total: items.length };
  }

  async getTask(userId: string, taskId: string): Promise<Task> {
    await delay(120);
    const task = this.tasks.get(taskId);
    if (!task || task.userId !== userId) throw ERRORS.NOT_FOUND("Task");
    return task;
  }

  async createTask(userId: string, input: CreateTaskInput): Promise<Task> {
    await delay();
    const task = this.toTask(userId, input);
    this.tasks.set(task.taskId, task);
    return task;
  }

  async updateTask(userId: string, taskId: string, input: UpdateTaskInput): Promise<Task> {
    await delay(150);
    const task = this.tasks.get(taskId);
    if (!task || task.userId !== userId) throw ERRORS.NOT_FOUND("Task");
    if (input.title !== undefined) {
      if (!input.title.trim()) FAILURE(400, "ERR_INVALID_TITLE", "Title cannot be blank");
      task.title = input.title;
    }
    if (input.description !== undefined) task.description = input.description;
    if (input.status !== undefined) {
      this.assertTaskStatus(input.status);
      task.status = input.status;
    }
    if (input.priority !== undefined) {
      this.assertTaskPriority(input.priority);
      task.priority = input.priority;
    }
    if (input.dueDate !== undefined) task.dueDate = input.dueDate;
    task.updatedAt = nowIso();
    return task;
  }

  async deleteTask(userId: string, taskId: string): Promise<void> {
    await delay(120);
    const task = this.tasks.get(taskId);
    if (!task || task.userId !== userId) throw ERRORS.NOT_FOUND("Task");
    this.tasks.delete(taskId);
  }
}

export function createMockApiClient(): ApiClient {
  return new MockApiClient();
}