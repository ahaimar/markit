import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { Input, Textarea } from "./Field";

describe("Input", () => {
  it("renders with label", () => {
    render(<Input label="Email" name="email" />);
    expect(screen.getByLabelText("Email")).toBeInTheDocument();
  });

  it("renders without label", () => {
    const { container } = render(<Input name="search" />);
    expect(container.querySelector("input")).toBeInTheDocument();
    expect(container.querySelector("label")).not.toBeInTheDocument();
  });

  it("displays error message", () => {
    render(<Input label="Email" name="email" error="Required" />);
    expect(screen.getByText("Required")).toBeInTheDocument();
    expect(screen.getByText("Required").className).toContain("text-red");
  });

  it("passes through input props", () => {
    render(<Input label="Name" name="name" type="text" placeholder="Enter name" />);
    const input = screen.getByPlaceholderText("Enter name");
    expect(input.getAttribute("type")).toBe("text");
  });

  it("applies error styling to input", () => {
    render(<Input label="Email" name="email" error="Invalid" />);
    const input = screen.getByLabelText("Email");
    expect(input.className).toContain("border-red");
  });
});

describe("Textarea", () => {
  it("renders with label", () => {
    render(<Textarea label="Address" name="address" />);
    expect(screen.getByLabelText("Address")).toBeInTheDocument();
  });

  it("displays error message", () => {
    render(<Textarea label="Notes" name="notes" error="Too long" />);
    expect(screen.getByText("Too long")).toBeInTheDocument();
  });

  it("passes through textarea props", () => {
    render(<Textarea label="Notes" name="notes" placeholder="Write here" rows={5} />);
    const textarea = screen.getByPlaceholderText("Write here");
    expect(textarea.getAttribute("rows")).toBe("5");
  });
});
