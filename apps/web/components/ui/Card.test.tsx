import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { Card, EmptyState } from "./Card";

describe("Card", () => {
  it("renders children", () => {
    render(<Card><p>Card content</p></Card>);
    expect(screen.getByText("Card content")).toBeInTheDocument();
  });

  it("applies default styling", () => {
    render(<Card><p>Test</p></Card>);
    const card = screen.getByText("Test").parentElement!;
    expect(card.className).toContain("rounded-xl");
    expect(card.className).toContain("border");
    expect(card.className).toContain("bg-white");
    expect(card.className).toContain("p-5");
    expect(card.className).toContain("shadow-sm");
  });

  it("applies custom className", () => {
    render(<Card className="custom-class"><p>Test</p></Card>);
    const card = screen.getByText("Test").parentElement!;
    expect(card.className).toContain("custom-class");
  });
});

describe("EmptyState", () => {
  it("renders title", () => {
    render(<EmptyState title="No items found" />);
    expect(screen.getByText("No items found")).toBeInTheDocument();
  });

  it("renders hint when provided", () => {
    render(<EmptyState title="No items" hint="Try adding some items" />);
    expect(screen.getByText("Try adding some items")).toBeInTheDocument();
  });

  it("does not render hint when not provided", () => {
    render(<EmptyState title="No items" />);
    expect(screen.queryByText("Try adding some items")).not.toBeInTheDocument();
  });

  it("renders children", () => {
    render(
      <EmptyState title="No items">
        <button>Add Item</button>
      </EmptyState>
    );
    expect(screen.getByText("Add Item")).toBeInTheDocument();
  });

  it("applies correct styling", () => {
    render(<EmptyState title="Empty" />);
    const container = screen.getByText("Empty").parentElement!;
    expect(container.className).toContain("flex");
    expect(container.className).toContain("flex-col");
    expect(container.className).toContain("items-center");
    expect(container.className).toContain("justify-center");
    expect(container.className).toContain("rounded-xl");
    expect(container.className).toContain("border-dashed");
  });
});
