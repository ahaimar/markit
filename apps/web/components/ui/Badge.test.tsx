import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { Badge, OrderStatusBadge, TaskStatusBadge, TaskPriorityBadge, Price } from "./Badge";

describe("Badge", () => {
  it("renders children", () => {
    render(<Badge>Test Badge</Badge>);
    expect(screen.getByText("Test Badge")).toBeInTheDocument();
  });

  it("applies neutral tone by default", () => {
    render(<Badge>Neutral</Badge>);
    const badge = screen.getByText("Neutral");
    expect(badge.className).toContain("bg-zinc-100");
    expect(badge.className).toContain("text-zinc-700");
  });

  it("applies success tone", () => {
    render(<Badge tone="success">Success</Badge>);
    const badge = screen.getByText("Success");
    expect(badge.className).toContain("bg-emerald-100");
    expect(badge.className).toContain("text-emerald-700");
  });

  it("applies warning tone", () => {
    render(<Badge tone="warning">Warning</Badge>);
    const badge = screen.getByText("Warning");
    expect(badge.className).toContain("bg-amber-100");
    expect(badge.className).toContain("text-amber-700");
  });

  it("applies danger tone", () => {
    render(<Badge tone="danger">Danger</Badge>);
    const badge = screen.getByText("Danger");
    expect(badge.className).toContain("bg-red-100");
    expect(badge.className).toContain("text-red-700");
  });
});

describe("Price", () => {
  it("formats price as USD", () => {
    render(<Price value={1234.56} />);
    expect(screen.getByText("$1,234.56")).toBeInTheDocument();
  });

  it("applies font-semibold class", () => {
    render(<Price value={9.99} />);
    expect(screen.getByText("$9.99").className).toContain("font-semibold");
  });

  it("applies custom className", () => {
    render(<Price value={9.99} className="text-lg" />);
    expect(screen.getByText("$9.99").className).toContain("text-lg");
  });
});

describe("OrderStatusBadge", () => {
  it("renders DELIVERED with success tone", () => {
    render(<OrderStatusBadge status="DELIVERED" />);
    const badge = screen.getByText("DELIVERED");
    expect(badge.className).toContain("bg-emerald-100");
  });

  it("renders CONFIRMED with success tone", () => {
    render(<OrderStatusBadge status="CONFIRMED" />);
    const badge = screen.getByText("CONFIRMED");
    expect(badge.className).toContain("bg-emerald-100");
  });

  it("renders PENDING with warning tone", () => {
    render(<OrderStatusBadge status="PENDING" />);
    const badge = screen.getByText("PENDING");
    expect(badge.className).toContain("bg-amber-100");
  });

  it("renders CANCELLED with danger tone", () => {
    render(<OrderStatusBadge status="CANCELLED" />);
    const badge = screen.getByText("CANCELLED");
    expect(badge.className).toContain("bg-red-100");
  });

  it("renders unknown status with neutral tone", () => {
    render(<OrderStatusBadge status="UNKNOWN" />);
    const badge = screen.getByText("UNKNOWN");
    expect(badge.className).toContain("bg-zinc-100");
  });
});

describe("TaskStatusBadge", () => {
  it("renders DONE with success tone", () => {
    render(<TaskStatusBadge status="DONE" />);
    const badge = screen.getByText("DONE");
    expect(badge.className).toContain("bg-emerald-100");
  });

  it("renders IN_PROGRESS with warning tone", () => {
    render(<TaskStatusBadge status="IN_PROGRESS" />);
    const badge = screen.getByText("IN_PROGRESS");
    expect(badge.className).toContain("bg-amber-100");
  });

  it("renders TODO with neutral tone", () => {
    render(<TaskStatusBadge status="TODO" />);
    const badge = screen.getByText("TODO");
    expect(badge.className).toContain("bg-zinc-100");
  });
});

describe("TaskPriorityBadge", () => {
  it("renders HIGH with danger tone", () => {
    render(<TaskPriorityBadge priority="HIGH" />);
    const badge = screen.getByText("HIGH");
    expect(badge.className).toContain("bg-red-100");
  });

  it("renders MEDIUM with warning tone", () => {
    render(<TaskPriorityBadge priority="MEDIUM" />);
    const badge = screen.getByText("MEDIUM");
    expect(badge.className).toContain("bg-amber-100");
  });

  it("renders LOW with neutral tone", () => {
    render(<TaskPriorityBadge priority="LOW" />);
    const badge = screen.getByText("LOW");
    expect(badge.className).toContain("bg-zinc-100");
  });
});
