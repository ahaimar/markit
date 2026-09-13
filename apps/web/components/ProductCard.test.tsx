import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ProductCard } from "./ProductCard";
import type { Product } from "@/lib/types";

const sampleProduct: Product = {
  id: "abc-123",
  name: "Ethiopian Yirgacheffe",
  description: "Bright, fruity, floral notes.",
  price: 18.99,
  category: "Coffee",
  stockQuantity: 10,
};

describe("ProductCard", () => {
  it("renders product name", () => {
    render(<ProductCard product={sampleProduct} />);
    expect(screen.getByText("Ethiopian Yirgacheffe")).toBeInTheDocument();
  });

  it("renders product price", () => {
    render(<ProductCard product={sampleProduct} />);
    expect(screen.getByText("$18.99")).toBeInTheDocument();
  });

  it("renders product category", () => {
    render(<ProductCard product={sampleProduct} />);
    expect(screen.getByText("Coffee")).toBeInTheDocument();
  });

  it("renders product description", () => {
    render(<ProductCard product={sampleProduct} />);
    expect(screen.getByText("Bright, fruity, floral notes.")).toBeInTheDocument();
  });

  it("shows in-stock count when stock > 0", () => {
    render(<ProductCard product={sampleProduct} />);
    expect(screen.getByText("10 in stock")).toBeInTheDocument();
  });

  it("shows 'Out of stock' when stock is 0", () => {
    const outOfStock = { ...sampleProduct, stockQuantity: 0 };
    render(<ProductCard product={outOfStock} />);
    expect(screen.getByText("Out of stock")).toBeInTheDocument();
  });

  it("links to product detail page", () => {
    render(<ProductCard product={sampleProduct} />);
    const link = screen.getByRole("link");
    expect(link.getAttribute("href")).toBe("/products/abc-123");
  });
});
