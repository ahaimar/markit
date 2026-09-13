import { describe, it, expect, beforeEach } from "vitest";
import { MockApiClient } from "./mockApiClient";
import type { RegisterInput, Task } from "@/lib/types";
import { ApiFailure } from "@/lib/types";

let client: MockApiClient;
const testUser: RegisterInput = { email: "test@example.com", name: "Test User", password: "Password1" };

beforeEach(() => {
  client = new MockApiClient();
});

describe("auth", () => {
  it("registers a new user", async () => {
    const res = await client.register(testUser);
    expect(res.accessToken).toBeDefined();
    expect(res.user.email).toBe("test@example.com");
    expect(res.user.name).toBe("Test User");
    expect(res.user.role).toBe("CUSTOMER");
  });

  it("rejects duplicate email", async () => {
    await client.register(testUser);
    await expect(client.register(testUser)).rejects.toThrow(ApiFailure);
  });

  it("logs in with correct credentials", async () => {
    await client.register(testUser);
    const res = await client.login({ email: "test@example.com", password: "Password1" });
    expect(res.accessToken).toBeDefined();
    expect(res.user.email).toBe("test@example.com");
  });

  it("rejects wrong password", async () => {
    await client.register(testUser);
    await expect(
      client.login({ email: "test@example.com", password: "wrong" }),
    ).rejects.toThrow(ApiFailure);
  });

  it("rejects login for non-existent user", async () => {
    await expect(
      client.login({ email: "noone@example.com", password: "Password1" }),
    ).rejects.toThrow(ApiFailure);
  });
});

describe("products", () => {
  it("lists products", async () => {
    const page = await client.listProducts({ page: 0, pageSize: 10 });
    expect(page.items.length).toBeGreaterThan(0);
    expect(page.total).toBeGreaterThan(0);
    expect(page.items[0].id).toBeDefined();
  });

  it("filters by category", async () => {
    const coffee = await client.listProducts({ page: 0, pageSize: 20, category: "Coffee" });
    for (const p of coffee.items) {
      expect(p.category).toBe("Coffee");
    }
  });

  it("filters by search term", async () => {
    const result = await client.listProducts({ page: 0, pageSize: 20, search: "kettle" });
    expect(result.items.length).toBeGreaterThanOrEqual(1);
    expect(result.items[0].name.toLowerCase()).toContain("kettle");
  });

  it("paginates", async () => {
    const page1 = await client.listProducts({ page: 0, pageSize: 2 });
    const page2 = await client.listProducts({ page: 1, pageSize: 2 });
    expect(page1.items).toHaveLength(2);
    expect(page1.items[0].id).not.toBe(page2.items[0].id);
  });

  it("gets a single product by id", async () => {
    const listing = await client.listProducts({ page: 0, pageSize: 1 });
    const product = await client.getProduct(listing.items[0].id);
    expect(product.id).toBe(listing.items[0].id);
  });

  it("throws on non-existent product", async () => {
    await expect(client.getProduct("nonexistent")).rejects.toThrow(ApiFailure);
  });
});

describe("cart", () => {
  async function registerAndGetId() {
    const res = await client.register(testUser);
    return res.user.id;
  }

  it("starts with an empty cart", async () => {
    const userId = await registerAndGetId();
    const cart = await client.getCart(userId);
    expect(cart.items).toHaveLength(0);
    expect(cart.totalPrice).toBe(0);
  });

  it("adds items to cart", async () => {
    const userId = await registerAndGetId();
    const products = await client.listProducts({ page: 0, pageSize: 1 });
    const pid = products.items[0].id;

    const cart = await client.addToCart(userId, pid, 2);
    expect(cart.items).toHaveLength(1);
    expect(cart.items[0].quantity).toBe(2);
    expect(cart.items[0].totalPrice).toBe(products.items[0].price * 2);
  });

  it("increments quantity when adding same product", async () => {
    const userId = await registerAndGetId();
    const products = await client.listProducts({ page: 0, pageSize: 1 });
    const pid = products.items[0].id;

    await client.addToCart(userId, pid, 1);
    const cart = await client.addToCart(userId, pid, 3);
    expect(cart.items).toHaveLength(1);
    expect(cart.items[0].quantity).toBe(4);
  });

  it("removes items from cart", async () => {
    const userId = await registerAndGetId();
    const products = await client.listProducts({ page: 0, pageSize: 1 });
    const pid = products.items[0].id;

    await client.addToCart(userId, pid, 1);
    const cart = await client.removeFromCart(userId, pid);
    expect(cart.items).toHaveLength(0);
  });

  it("clears cart", async () => {
    const userId = await registerAndGetId();
    const products = await client.listProducts({ page: 0, pageSize: 2 });

    await client.addToCart(userId, products.items[0].id, 1);
    await client.addToCart(userId, products.items[1].id, 1);
    await client.clearCart(userId);

    const cart = await client.getCart(userId);
    expect(cart.items).toHaveLength(0);
  });

  it("rejects adding out-of-stock product", async () => {
    const userId = await registerAndGetId();
    const products = await client.listProducts({ page: 0, pageSize: 1 });
    const pid = products.items[0].id;
    const stock = products.items[0].stockQuantity;

    await expect(client.addToCart(userId, pid, stock + 10)).rejects.toThrow(ApiFailure);
  });
});

describe("orders", () => {
  async function setupWithCart() {
    const res = await client.register(testUser);
    const userId = res.user.id;
    const products = await client.listProducts({ page: 0, pageSize: 1 });
    await client.addToCart(userId, products.items[0].id, 1);
    return { userId, product: products.items[0] };
  }

  it("places an order", async () => {
    const { userId } = await setupWithCart();
    const order = await client.placeOrder(userId, { cartId: "test", shippingAddress: "123 Main St" });

    expect(order.orderId).toBeDefined();
    expect(order.status).toBe("PENDING");
    expect(order.items).toHaveLength(1);
    expect(order.shippingAddress).toBe("123 Main St");
  });

  it("lists orders after placing one", async () => {
    const { userId } = await setupWithCart();
    await client.placeOrder(userId, { cartId: "test", shippingAddress: "123 Main St" });

    const listing = await client.listOrders(userId);
    expect(listing.items).toHaveLength(1);
  });

  it("gets order by id", async () => {
    const { userId } = await setupWithCart();
    const placed = await client.placeOrder(userId, { cartId: "test", shippingAddress: "123 Main St" });
    const fetched = await client.getOrder(userId, placed.orderId);

    expect(fetched.orderId).toBe(placed.orderId);
    expect(fetched.totalPrice).toBe(placed.totalPrice);
  });

  it("clears cart after placing order", async () => {
    const { userId } = await setupWithCart();
    await client.placeOrder(userId, { cartId: "test", shippingAddress: "123 Main St" });

    const cart = await client.getCart(userId);
    expect(cart.items).toHaveLength(0);
  });
});

describe("profile", () => {
  it("updates profile", async () => {
    const res = await client.register(testUser);
    const user = await client.updateProfile(res.user.id, { name: "New Name" });
    expect(user.name).toBe("New Name");
  });

  it("changes password", async () => {
    const res = await client.register(testUser);
    await client.changePassword(res.user.id, {
      currentPassword: "Password1",
      newPassword: "NewPassword2",
    });

    const login = await client.login({ email: testUser.email, password: "NewPassword2" });
    expect(login.accessToken).toBeDefined();
  });

  it("rejects wrong current password", async () => {
    const res = await client.register(testUser);
    await expect(
      client.changePassword(res.user.id, {
        currentPassword: "wrong",
        newPassword: "NewPassword2",
      }),
    ).rejects.toThrow(ApiFailure);
  });
});

describe("tasks", () => {
  async function registerAndGetId() {
    const res = await client.register(testUser);
    return res.user.id;
  }

  it("starts with no tasks", async () => {
    const userId = await registerAndGetId();
    const listing = await client.listTasks(userId);
    expect(listing.items).toHaveLength(0);
    expect(listing.total).toBe(0);
  });

  it("creates a task with defaults", async () => {
    const userId = await registerAndGetId();
    const task = await client.createTask(userId, { title: "Roast a batch" });

    expect(task.taskId).toBeDefined();
    expect(task.title).toBe("Roast a batch");
    expect(task.status).toBe("TODO");
    expect(task.priority).toBe("MEDIUM");
    expect(task.description).toBeNull();
  });

  it("creates a task with explicit fields", async () => {
    const userId = await registerAndGetId();
    const task = await client.createTask(userId, {
      title: "Restock mugs",
      description: "Order 20 ceramic mugs.",
      status: "IN_PROGRESS",
      priority: "HIGH",
      dueDate: "2026-09-20T10:00:00.000Z",
    });

    expect(task.status).toBe("IN_PROGRESS");
    expect(task.priority).toBe("HIGH");
    expect(task.dueDate).toBe("2026-09-20T10:00:00.000Z");
  });

  it("rejects invalid status", async () => {
    const userId = await registerAndGetId();
    await expect(
      client.createTask(userId, { title: "Bad", status: "BOGUS" as Task["status"] }),
    ).rejects.toThrow(ApiFailure);
  });

  it("lists and filters tasks", async () => {
    const userId = await registerAndGetId();
    await client.createTask(userId, { title: "Task A", status: "TODO" });
    await client.createTask(userId, { title: "Task B", status: "DONE" });

    const all = await client.listTasks(userId);
    expect(all.total).toBe(2);

    const done = await client.listTasks(userId, { status: "DONE" });
    expect(done.items).toHaveLength(1);
    expect(done.items[0].title).toBe("Task B");
  });

  it("isolates tasks between users", async () => {
    const userId = await registerAndGetId();
    await client.createTask(userId, { title: "Private task" });

    await client.register({ email: "other@example.com", name: "Other", password: "Password1" });
    const res2 = await client.login({ email: "other@example.com", password: "Password1" });
    const otherListing = await client.listTasks(res2.user.id);
    expect(otherListing.total).toBe(0);
  });

  it("updates a task", async () => {
    const userId = await registerAndGetId();
    const task = await client.createTask(userId, { title: "Task A" });

    const updated = await client.updateTask(userId, task.taskId, { status: "DONE", priority: "LOW" });

    expect(updated.status).toBe("DONE");
    expect(updated.priority).toBe("LOW");
    expect(updated.title).toBe("Task A");
  });

  it("rejects blank title on update", async () => {
    const userId = await registerAndGetId();
    const task = await client.createTask(userId, { title: "Task A" });

    await expect(
      client.updateTask(userId, task.taskId, { title: "   " }),
    ).rejects.toThrow(ApiFailure);
  });

  it("gets a task by id", async () => {
    const userId = await registerAndGetId();
    const created = await client.createTask(userId, { title: "Task A" });

    const fetched = await client.getTask(userId, created.taskId);
    expect(fetched.taskId).toBe(created.taskId);
  });

  it("throws when accessing another user's task", async () => {
    const userId = await registerAndGetId();
    const task = await client.createTask(userId, { title: "Secret" });

    await client.register({ email: "other@example.com", name: "Other", password: "Password1" });
    const res2 = await client.login({ email: "other@example.com", password: "Password1" });

    await expect(client.getTask(res2.user.id, task.taskId)).rejects.toThrow(ApiFailure);
    await expect(client.deleteTask(res2.user.id, task.taskId)).rejects.toThrow(ApiFailure);
  });

  it("deletes a task", async () => {
    const userId = await registerAndGetId();
    const task = await client.createTask(userId, { title: "Task A" });

    await client.deleteTask(userId, task.taskId);

    const listing = await client.listTasks(userId);
    expect(listing.total).toBe(0);
    await expect(client.getTask(userId, task.taskId)).rejects.toThrow(ApiFailure);
  });
});
