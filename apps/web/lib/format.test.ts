import { describe, it, expect } from "vitest";
import { formatPrice, pluralise } from "./format";

describe("formatPrice", () => {
  it("formats zero", () => {
    expect(formatPrice(0)).toBe("$0.00");
  });

  it("formats whole dollars", () => {
    expect(formatPrice(25)).toBe("$25.00");
  });

  it("formats decimals", () => {
    expect(formatPrice(9.99)).toBe("$9.99");
  });

  it("formats large numbers with commas", () => {
    expect(formatPrice(1234567.89)).toBe("$1,234,567.89");
  });

  it("formats negative values", () => {
    expect(formatPrice(-42.5)).toBe("-$42.50");
  });
});

describe("pluralise", () => {
  it("returns singular for count 1", () => {
    expect(pluralise(1, "item", "items")).toBe("item");
  });

  it("returns plural for count 0", () => {
    expect(pluralise(0, "item", "items")).toBe("items");
  });

  it("returns plural for count > 1", () => {
    expect(pluralise(5, "item", "items")).toBe("items");
  });
});
