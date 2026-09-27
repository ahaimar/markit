import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { Footer } from "./Footer";

describe("Footer", () => {
  it("renders copyright with current year", () => {
    render(<Footer />);
    const currentYear = new Date().getFullYear();
    expect(screen.getByText(`© ${currentYear} Markit. All rights reserved.`)).toBeInTheDocument();
  });

  it("renders Shop link", () => {
    render(<Footer />);
    const shopLink = screen.getByRole("link", { name: "Shop" });
    expect(shopLink).toBeInTheDocument();
    expect(shopLink).toHaveAttribute("href", "/products");
  });

  it("renders Orders link", () => {
    render(<Footer />);
    const ordersLink = screen.getByRole("link", { name: "Orders" });
    expect(ordersLink).toBeInTheDocument();
    expect(ordersLink).toHaveAttribute("href", "/orders");
  });
});
