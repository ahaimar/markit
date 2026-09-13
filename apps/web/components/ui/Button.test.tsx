import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Button } from "./Button";

describe("Button", () => {
  it("renders children text", () => {
    render(<Button>Click me</Button>);
    expect(screen.getByRole("button", { name: "Click me" })).toBeInTheDocument();
  });

  it("calls onClick when clicked", async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(<Button onClick={onClick}>Click</Button>);
    await user.click(screen.getByRole("button"));
    expect(onClick).toHaveBeenCalledOnce();
  });

  it("is disabled when disabled prop is true", () => {
    render(<Button disabled>Click</Button>);
    expect(screen.getByRole("button")).toBeDisabled();
  });

  it("is disabled when loading", () => {
    render(<Button loading>Click</Button>);
    expect(screen.getByRole("button")).toBeDisabled();
  });

  it("shows spinner when loading", () => {
    render(<Button loading>Click</Button>);
    expect(screen.getByRole("button").querySelector("span.animate-spin")).toBeInTheDocument();
  });

  it("applies primary variant by default", () => {
    render(<Button>Click</Button>);
    const btn = screen.getByRole("button");
    expect(btn.className).toContain("bg-zinc-900");
  });

  it("applies secondary variant", () => {
    render(<Button variant="secondary">Click</Button>);
    const btn = screen.getByRole("button");
    expect(btn.className).toContain("bg-zinc-100");
  });

  it("applies ghost variant", () => {
    render(<Button variant="ghost">Click</Button>);
    const btn = screen.getByRole("button");
    expect(btn.className).toContain("hover:bg-zinc-100");
  });

  it("applies custom className", () => {
    render(<Button className="my-custom-class">Click</Button>);
    expect(screen.getByRole("button").className).toContain("my-custom-class");
  });

  it("passes through HTML attributes", () => {
    render(<Button type="submit">Submit</Button>);
    expect(screen.getByRole("button").getAttribute("type")).toBe("submit");
  });
});
