import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  apiErrorResponse,
  jsonResponse,
  makeMovement,
  makeProduct,
  routeFetch,
} from "@/test/fixtures";
import { ProductDetail } from "./ProductDetail";

const { push } = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push, replace: vi.fn() }),
}));

const fetchMock = vi.fn();

beforeEach(() => {
  fetchMock.mockReset();
  push.mockReset();
  vi.stubGlobal("fetch", fetchMock);
  vi.stubEnv("NEXT_PUBLIC_API_URL", "http://api.test");
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

function calls(method: string, path: string) {
  return fetchMock.mock.calls.filter(([url, init]) => {
    const u = new URL(url as string);
    return (
      u.pathname === path &&
      ((init as RequestInit | undefined)?.method ?? "GET").toUpperCase() === method
    );
  });
}

function quantityShown(): string | null {
  return screen.getByText("Quantidade em estoque").nextElementSibling?.textContent ?? null;
}

const product = makeProduct({ id: 1 });

describe("ProductDetail — dados e badge", () => {
  it("mostra carregando e depois todos os dados do produto", async () => {
    routeFetch(fetchMock, {
      "GET /product/1": () => jsonResponse(product),
      "GET /movements": () => jsonResponse([]),
    });
    render(<ProductDetail productId="1" />);

    expect(screen.getAllByRole("status")[0]).toHaveTextContent("Carregando");
    expect(await screen.findByRole("heading", { level: 1, name: "Mouse Gamer" })).toBeInTheDocument();
    expect(screen.getByText("MOUSE-001")).toBeInTheDocument();
    expect(screen.getByText("Mouse com fio")).toBeInTheDocument();
    expect(screen.getByText(/R\$\s?129,90/)).toBeInTheDocument();
    expect(quantityShown()).toBe("25");
    expect(screen.getByText("Estoque mínimo").nextElementSibling).toHaveTextContent("10");
    expect(screen.getByText("Criado em").nextElementSibling).toHaveTextContent(/30\/10\/2026.*10:00/);
    expect(screen.getByRole("link", { name: "Editar" })).toHaveAttribute("href", "/products/1/edit");
    expect(screen.getByRole("button", { name: "Excluir" })).toBeInTheDocument();
  });

  it("exibe o badge 'Estoque baixo' conforme o campo low_stock da API", async () => {
    routeFetch(fetchMock, {
      "GET /product/1": () => jsonResponse(makeProduct({ quantity: 8, min_stock: 10, low_stock: true })),
      "GET /movements": () => jsonResponse([]),
    });
    render(<ProductDetail productId="1" />);

    expect(await screen.findByText("Estoque baixo")).toBeInTheDocument();
  });

  it("não recalcula low_stock no cliente: low_stock=false não mostra badge mesmo com quantity < min_stock", async () => {
    routeFetch(fetchMock, {
      "GET /product/1": () => jsonResponse(makeProduct({ quantity: 1, min_stock: 10, low_stock: false })),
      "GET /movements": () => jsonResponse([]),
    });
    render(<ProductDetail productId="1" />);

    await screen.findByRole("heading", { level: 1 });
    expect(screen.queryByText("Estoque baixo")).not.toBeInTheDocument();
  });

  it("404: mensagem apropriada, link para a lista e para o histórico", async () => {
    routeFetch(fetchMock, {
      "GET /product/5": () => apiErrorResponse(404, "Produto não encontrado"),
      "GET /movements": () => jsonResponse([]),
    });
    render(<ProductDetail productId="5" />);

    expect(await screen.findByText("Produto não encontrado")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Voltar para produtos" })).toHaveAttribute("href", "/products");
    expect(screen.getByRole("link", { name: /histórico de movimentações/i })).toHaveAttribute(
      "href",
      "/movements?product_id=5",
    );
    expect(screen.queryByRole("button", { name: "Excluir" })).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Quantidade")).not.toBeInTheDocument();
  });

  it("erro de carregamento do produto oferece 'Tentar novamente'", async () => {
    const user = userEvent.setup();
    let n = 0;
    routeFetch(fetchMock, {
      "GET /product/1": () => {
        n += 1;
        return n === 1 ? apiErrorResponse(500, "Erro interno") : jsonResponse(product);
      },
      "GET /movements": () => jsonResponse([]),
    });
    render(<ProductDetail productId="1" />);

    expect(await screen.findByText("Não foi possível carregar o produto")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Tentar novamente" }));
    expect(await screen.findByRole("heading", { level: 1, name: "Mouse Gamer" })).toBeInTheDocument();
  });
});

describe("ProductDetail — exclusão", () => {
  beforeEach(() => {
    routeFetch(fetchMock, {
      "GET /product/1": () => jsonResponse(product),
      "GET /movements": () => jsonResponse([]),
      "DELETE /product/1": () => new Response(null, { status: 204 }),
    });
  });

  it("pede confirmação explicando exclusão lógica e histórico preservado; cancelar não exclui", async () => {
    const user = userEvent.setup();
    render(<ProductDetail productId="1" />);

    await user.click(await screen.findByRole("button", { name: "Excluir" }));

    const dialog = screen.getByRole("alertdialog", { name: "Excluir produto?" });
    expect(dialog).toHaveAttribute("aria-modal", "true");
    expect(dialog).toHaveAccessibleDescription(/exclusão é lógica/);
    expect(dialog).toHaveTextContent(/histórico de movimentações são preservados/);
    expect(within(dialog).getByRole("button", { name: "Cancelar" })).toHaveFocus();

    await user.click(within(dialog).getByRole("button", { name: "Cancelar" }));

    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(calls("DELETE", "/product/1")).toHaveLength(0);
    expect(push).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Excluir" })).toHaveFocus();
  });

  it("Esc fecha o diálogo sem excluir", async () => {
    const user = userEvent.setup();
    render(<ProductDetail productId="1" />);

    await user.click(await screen.findByRole("button", { name: "Excluir" }));
    await user.keyboard("{Escape}");

    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(calls("DELETE", "/product/1")).toHaveLength(0);
  });

  it("confirmar chama DELETE (204) e redireciona para /products com feedback", async () => {
    const user = userEvent.setup();
    render(<ProductDetail productId="1" />);

    await user.click(await screen.findByRole("button", { name: "Excluir" }));
    await user.click(screen.getByRole("button", { name: "Excluir produto" }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/products?deleted=1"));
    expect(calls("DELETE", "/product/1")).toHaveLength(1);
  });

  it("falha ao excluir mostra a mensagem da API e fecha o diálogo", async () => {
    const user = userEvent.setup();
    routeFetch(fetchMock, {
      "GET /product/1": () => jsonResponse(product),
      "GET /movements": () => jsonResponse([]),
      "DELETE /product/1": () => apiErrorResponse(404, "Produto não encontrado"),
    });
    render(<ProductDetail productId="1" />);

    await user.click(await screen.findByRole("button", { name: "Excluir" }));
    await user.click(screen.getByRole("button", { name: "Excluir produto" }));

    expect(await screen.findByText("Não foi possível excluir o produto")).toBeInTheDocument();
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(push).not.toHaveBeenCalled();
  });
});

describe("ProductDetail — formulário de entrada/saída", () => {
  it("quantidade deve ser maior que zero: não chama a API", async () => {
    const user = userEvent.setup();
    routeFetch(fetchMock, {
      "GET /product/1": () => jsonResponse(product),
      "GET /movements": () => jsonResponse([]),
    });
    render(<ProductDetail productId="1" />);

    await screen.findByRole("heading", { level: 1 });
    await user.type(screen.getByLabelText("Quantidade"), "0");
    await user.click(screen.getByRole("button", { name: "Registrar movimentação" }));

    expect(screen.getByText("A quantidade deve ser maior que zero")).toBeInTheDocument();
    expect(calls("POST", "/movements")).toHaveLength(0);
  });

  it("sucesso: POST /movements e recarrega produto e histórico do backend", async () => {
    const user = userEvent.setup();
    let stock = 25;
    let history = [makeMovement({ id: 1, type: "ENTRY", quantity: 25 })];
    const bodies: unknown[] = [];
    routeFetch(fetchMock, {
      "GET /product/1": () => jsonResponse(makeProduct({ quantity: stock })),
      "GET /movements": () => jsonResponse(history),
      "POST /movements": ({ body }) => {
        bodies.push(body);
        stock = 20;
        history = [
          makeMovement({ id: 2, type: "EXIT", quantity: 5, description: "Separacao de pedidos" }),
          ...history,
        ];
        return jsonResponse(
          makeMovement({ id: 2, type: "EXIT", quantity: 5, stock_after: 20 }),
          201,
        );
      },
    });
    render(<ProductDetail productId="1" />);

    await screen.findByRole("heading", { level: 1 });
    expect(quantityShown()).toBe("25");

    await user.selectOptions(screen.getByLabelText("Tipo"), "EXIT");
    await user.type(screen.getByLabelText("Quantidade"), "5");
    await user.type(screen.getByLabelText(/Descrição/), "Separacao de pedidos");
    await user.click(screen.getByRole("button", { name: "Registrar movimentação" }));

    await waitFor(() => expect(quantityShown()).toBe("20"));
    expect(bodies).toEqual([
      { product_id: 1, type: "EXIT", quantity: 5, description: "Separacao de pedidos" },
    ]);
    // produto e histórico foram recarregados do servidor
    expect(calls("GET", "/product/1")).toHaveLength(2);
    expect(calls("GET", "/movements")).toHaveLength(2);
    const table = await screen.findByRole("table", { name: "Histórico do produto" });
    expect(await within(table).findByText("Separacao de pedidos")).toBeInTheDocument();
    // feedback pode usar stock_after
    expect(screen.getByText(/Saldo após a operação: 20/)).toBeInTheDocument();
    // campos limpos para a próxima operação
    expect(screen.getByLabelText("Quantidade")).toHaveValue("");
  });

  it("409 (estoque insuficiente): mostra a mensagem da API e não altera os dados exibidos", async () => {
    const user = userEvent.setup();
    routeFetch(fetchMock, {
      "GET /product/1": () => jsonResponse(product),
      "GET /movements": () => jsonResponse([makeMovement({ id: 1, quantity: 25 })]),
      "POST /movements": () =>
        apiErrorResponse(409, "Estoque insuficiente: saldo atual 25, solicitado 99."),
    });
    render(<ProductDetail productId="1" />);

    await screen.findByRole("heading", { level: 1 });
    await user.selectOptions(screen.getByLabelText("Tipo"), "EXIT");
    await user.type(screen.getByLabelText("Quantidade"), "99");
    await user.click(screen.getByRole("button", { name: "Registrar movimentação" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Estoque insuficiente: saldo atual 25, solicitado 99.");
    expect(quantityShown()).toBe("25");
    // nada recarregado: dados exibidos permanecem os do backend anterior
    expect(calls("GET", "/product/1")).toHaveLength(1);
    expect(calls("GET", "/movements")).toHaveLength(1);
    // a quantidade digitada é mantida para o usuário ajustar
    expect(screen.getByLabelText("Quantidade")).toHaveValue("99");
    expect(screen.getByRole("button", { name: "Registrar movimentação" })).toBeEnabled();
  });

  it("400 com field_errors aparece junto dos campos", async () => {
    const user = userEvent.setup();
    routeFetch(fetchMock, {
      "GET /product/1": () => jsonResponse(product),
      "GET /movements": () => jsonResponse([]),
      "POST /movements": () =>
        apiErrorResponse(400, "Validação falhou", {
          quantity: "A quantidade deve ser maior que zero (servidor)",
          description: "Descrição longa demais (servidor)",
        }),
    });
    render(<ProductDetail productId="1" />);

    await screen.findByRole("heading", { level: 1 });
    await user.type(screen.getByLabelText("Quantidade"), "3");
    await user.click(screen.getByRole("button", { name: "Registrar movimentação" }));

    expect(
      await screen.findByText("A quantidade deve ser maior que zero (servidor)"),
    ).toBeInTheDocument();
    expect(screen.getByText("Descrição longa demais (servidor)")).toBeInTheDocument();
    expect(screen.getByLabelText("Quantidade")).toHaveAttribute("aria-invalid", "true");
  });

  it("impede duplo envio enquanto a requisição está em andamento", async () => {
    const user = userEvent.setup();
    let release!: () => void;
    routeFetch(fetchMock, {
      "GET /product/1": () => jsonResponse(product),
      "GET /movements": () => jsonResponse([]),
      "POST /movements": () =>
        new Promise<Response>((resolve) => {
          release = () => resolve(jsonResponse(makeMovement({ stock_after: 30 }), 201));
        }) as unknown as Response,
    });
    render(<ProductDetail productId="1" />);

    await screen.findByRole("heading", { level: 1 });
    await user.type(screen.getByLabelText("Quantidade"), "5");
    const button = screen.getByRole("button", { name: "Registrar movimentação" });
    await user.click(button);
    await user.click(button);
    await user.click(button);

    expect(button).toBeDisabled();
    expect(calls("POST", "/movements")).toHaveLength(1);

    release();
    await waitFor(() => expect(button).toBeEnabled());
    expect(calls("POST", "/movements")).toHaveLength(1);
  });
});

describe("ProductDetail — histórico", () => {
  function setup(movementsHandler: () => Response) {
    routeFetch(fetchMock, {
      "GET /product/1": () => jsonResponse(product),
      "GET /movements": movementsHandler,
    });
  }

  it("estado vazio", async () => {
    setup(() => jsonResponse([]));
    render(<ProductDetail productId="1" />);

    expect(await screen.findByText("Nenhuma movimentação registrada")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("estado de carregamento", async () => {
    routeFetch(fetchMock, {
      "GET /product/1": () => jsonResponse(product),
      "GET /movements": () => new Promise<Response>(() => {}) as unknown as Response,
    });
    render(<ProductDetail productId="1" />);

    expect(await screen.findByText("Carregando histórico…")).toBeInTheDocument();
  });

  it("erro com 'Tentar novamente'", async () => {
    const user = userEvent.setup();
    let n = 0;
    setup(() => {
      n += 1;
      return n === 1 ? apiErrorResponse(500, "Falha ao consultar histórico") : jsonResponse([makeMovement()]);
    });
    render(<ProductDetail productId="1" />);

    expect(await screen.findByText("Falha ao consultar histórico")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Tentar novamente" }));
    expect(await screen.findByRole("table", { name: "Histórico do produto" })).toBeInTheDocument();
  });

  it("renderiza na ordem da API, com tipo distinto, quantidade, descrição e data pt-BR; consulta por product_id", async () => {
    setup(() =>
      jsonResponse([
        makeMovement({ id: 3, type: "EXIT", quantity: 7, description: "Saída recente", created_at: "2026-10-30T12:30:00" }),
        makeMovement({ id: 2, type: "ENTRY", quantity: 9, description: "Reposição", created_at: "2026-10-29T08:00:00" }),
        makeMovement({ id: 1, type: "ENTRY", quantity: 25, description: null, created_at: "2026-10-28T09:15:00" }),
      ]),
    );
    render(<ProductDetail productId="1" />);

    const table = await screen.findByRole("table", { name: "Histórico do produto" });
    const rows = within(table).getAllByRole("row").slice(1);
    expect(rows).toHaveLength(3);
    expect(rows[0]).toHaveTextContent("Saída");
    expect(rows[0]).toHaveTextContent("7");
    expect(rows[0]).toHaveTextContent("Saída recente");
    expect(rows[0]).toHaveTextContent(/30\/10\/2026.*12:30/);
    expect(rows[1]).toHaveTextContent("Entrada");
    expect(rows[1]).toHaveTextContent("Reposição");
    expect(rows[1]).toHaveTextContent(/29\/10\/2026.*08:00/);
    expect(rows[2]).toHaveTextContent("25");
    expect(rows[2]).toHaveTextContent("—");

    // badges distintos por tipo
    expect(within(rows[0]).getByText("Saída").className).not.toEqual(
      within(rows[1]).getByText("Entrada").className,
    );
    // versão em cartões também existe (responsivo)
    expect(screen.getByRole("list", { name: /Histórico do produto em cartões/ })).toBeInTheDocument();

    const historyCall = calls("GET", "/movements")[0][0] as string;
    expect(new URL(historyCall).searchParams.get("product_id")).toBe("1");
    expect(screen.getByRole("link", { name: "Ver histórico completo" })).toHaveAttribute(
      "href",
      "/movements?product_id=1",
    );
  });

  it("limita o detalhe às 10 movimentações mais recentes e avisa", async () => {
    setup(() =>
      jsonResponse(
        Array.from({ length: 12 }, (_, i) => makeMovement({ id: 100 - i, quantity: i + 1 })),
      ),
    );
    render(<ProductDetail productId="1" />);

    const table = await screen.findByRole("table", { name: "Histórico do produto" });
    expect(within(table).getAllByRole("row")).toHaveLength(11); // cabeçalho + 10
    expect(screen.getByText(/Exibindo as 10 movimentações mais recentes de 12/)).toBeInTheDocument();
  });
});
