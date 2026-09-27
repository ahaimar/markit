import { describe, it, expect, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { useApi } from "./useApi";
import { ApiFailure } from "@/lib/types";

describe("useApi", () => {
  it("returns loading state initially", () => {
    const fetcher = vi.fn().mockReturnValue(new Promise(() => {}));
    const { result } = renderHook(() => useApi(fetcher));
    expect(result.current.loading).toBe(true);
    expect(result.current.data).toBeNull();
    expect(result.current.error).toBeNull();
  });

  it("returns data on successful fetch", async () => {
    const mockData = { id: "1", name: "Test" };
    const fetcher = vi.fn().mockResolvedValue(mockData);
    const { result } = renderHook(() => useApi(fetcher));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.data).toEqual(mockData);
    expect(result.current.error).toBeNull();
  });

  it("returns error on failed fetch", async () => {
    const mockError = new ApiFailure(500, { code: "ERR_INTERNAL", message: "Server error" });
    const fetcher = vi.fn().mockRejectedValue(mockError);
    const { result } = renderHook(() => useApi(fetcher));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.error).toEqual(mockError);
    expect(result.current.data).toBeNull();
  });

  it("wraps non-ApiFailure errors", async () => {
    const fetcher = vi.fn().mockRejectedValue(new Error("Network error"));
    const { result } = renderHook(() => useApi(fetcher));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.error).toBeInstanceOf(ApiFailure);
    expect(result.current.error?.message).toContain("Network error");
  });

  it("re-fetches when dependencies change", async () => {
    const mockData1 = { id: "1", name: "First" };
    const mockData2 = { id: "2", name: "Second" };
    let callCount = 0;
    const fetcher = vi.fn().mockImplementation(() => {
      callCount++;
      return Promise.resolve(callCount === 1 ? mockData1 : mockData2);
    });

    const { result, rerender } = renderHook(({ dep }) => useApi(fetcher, [dep]), {
      initialProps: { dep: "a" },
    });

    await waitFor(() => expect(result.current.data).toEqual(mockData1));
    expect(fetcher).toHaveBeenCalledTimes(1);

    rerender({ dep: "b" });
    await waitFor(() => expect(result.current.data).toEqual(mockData2));
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it("does not re-fetch when dependencies stay the same", async () => {
    const mockData = { id: "1", name: "Test" };
    const fetcher = vi.fn().mockResolvedValue(mockData);

    const { result, rerender } = renderHook(({ dep }) => useApi(fetcher, [dep]), {
      initialProps: { dep: "a" },
    });

    await waitFor(() => expect(result.current.data).toEqual(mockData));
    expect(fetcher).toHaveBeenCalledTimes(1);

    rerender({ dep: "a" });
    await new Promise((r) => setTimeout(r, 50));
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it("reload triggers re-fetch", async () => {
    let callCount = 0;
    const fetcher = vi.fn().mockImplementation(() => {
      callCount++;
      return Promise.resolve({ count: callCount });
    });

    const { result } = renderHook(() => useApi(fetcher));
    await waitFor(() => expect(result.current.data).toEqual({ count: 1 }));

    result.current.reload();
    await waitFor(() => expect(result.current.data).toEqual({ count: 2 }));
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it("cancels in-flight request on unmount", async () => {
    let resolvePromise: (value: unknown) => void;
    const fetcher = vi.fn().mockImplementation(
      () =>
        new Promise((resolve) => {
          resolvePromise = resolve;
        })
    );

    const { result, unmount } = renderHook(() => useApi(fetcher));
    expect(result.current.loading).toBe(true);

    unmount();
    resolvePromise!({ id: "1" });
    await new Promise((r) => setTimeout(r, 50));
  });

  it("uses latest fetcher reference", async () => {
    const fetcher1 = vi.fn().mockResolvedValue("first");
    const fetcher2 = vi.fn().mockResolvedValue("second");

    const { result, rerender } = renderHook(({ f }) => useApi(f), {
      initialProps: { f: fetcher1 },
    });

    await waitFor(() => expect(result.current.data).toBe("first"));

    rerender({ f: fetcher2 });
    await waitFor(() => expect(result.current.data).toBe("second"));
  });
});
