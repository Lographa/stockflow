import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { DeletedNotice } from "./DeletedNotice";

const nav = vi.hoisted(() => ({ search: new URLSearchParams() }));
vi.mock("next/navigation", () => ({
  useSearchParams: () => nav.search,
}));

beforeEach(() => {
  nav.search = new URLSearchParams();
});

describe("DeletedNotice", () => {
  it("mostra o feedback de exclusão com ?deleted=1", () => {
    nav.search = new URLSearchParams("deleted=1");
    render(<DeletedNotice />);
    expect(screen.getByRole("status")).toHaveTextContent("Produto excluído com sucesso");
  });

  it("não mostra nada sem o parâmetro", () => {
    const { container } = render(<DeletedNotice />);
    expect(container).toBeEmptyDOMElement();
  });
});
