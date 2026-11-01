import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  apiErrorResponse,
  jsonResponse,
  makeProduct,
  routeFetch,
} from "@/test/fixtures";
import { EditProduct } from "./EditProduct";
import { NewProduct } from "./NewProduct";
import { QUANTITY_ADJUSTMENT_WARNING } from "./ProductForm";

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

describe("NewProduct (/products/new)", () => {
  async function fill(user: ReturnType<typeof userEvent.setup>) {
    await user.type(screen.getByLabelText("SKU"), "MOUSE-001");
    await user.type(screen.getByLabelText("Nome"), "Mouse Gamer");
    await user.type(screen.getByLabelText(/Preço/), "129,90");
    const quantity = screen.getByLabelText("Quantidade");
    await user.clear(quantity);
    await user.type(quantity, "25");
    const min = screen.getByLabelText("Estoque mínimo");
    await user.clear(min);
    await user.type(min, "10");
  }

  it("cria o produto (POST /product/create, 201) e redireciona para o detalhe", async () => {
    const user = userEvent.setup();
    const bodies: unknown[] = [];
    routeFetch(fetchMock, {
      "POST /product/create": ({ body }) => {
        bodies.push(body);
        return jsonResponse(makeProduct({ id: 7 }), 201);
      },
    });
    render(<NewProduct />);

    await fill(user);
    await user.click(screen.getByRole("button", { name: "Cadastrar produto" }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/products/7"));
    expect(bodies).toEqual([
      { sku: "MOUSE-001", name: "Mouse Gamer", price: 129.9, quantity: 25, min_stock: 10 },
    ]);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("409: mostra erro, não redireciona e permite nova tentativa", async () => {
    const user = userEvent.setup();
    routeFetch(fetchMock, {
      "POST /product/create": () =>
        apiErrorResponse(409, "Já existe um produto com o SKU MOUSE-001."),
    });
    render(<NewProduct />);

    await fill(user);
    await user.click(screen.getByRole("button", { name: "Cadastrar produto" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("SKU ou nome já cadastrado");
    expect(push).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Cadastrar produto" })).toBeEnabled();
  });

  it("400 com field_errors aparece junto dos campos", async () => {
    const user = userEvent.setup();
    routeFetch(fetchMock, {
      "POST /product/create": () =>
        apiErrorResponse(400, "Validação falhou", { name: "Nome inválido (servidor)" }),
    });
    render(<NewProduct />);

    await fill(user);
    await user.click(screen.getByRole("button", { name: "Cadastrar produto" }));

    expect(await screen.findByText("Nome inválido (servidor)")).toBeInTheDocument();
    expect(screen.getByLabelText("Nome")).toHaveAttribute("aria-invalid", "true");
    expect(push).not.toHaveBeenCalled();
  });
});

describe("EditProduct (/products/[id]/edit)", () => {
  const product = makeProduct({ id: 3, description: "Mouse com fio" });

  it("pré-carrega com GET /product/{id} e mostra o carregamento", async () => {
    routeFetch(fetchMock, { "GET /product/3": () => jsonResponse(product) });
    render(<EditProduct productId="3" />);

    expect(screen.getByRole("status")).toHaveTextContent("Carregando produto");
    expect(await screen.findByLabelText("SKU")).toHaveValue("MOUSE-001");
    expect(screen.getByLabelText("Nome")).toHaveValue("Mouse Gamer");
  });

  it("alterar a quantidade exibe o aviso e envia PUT só com o campo alterado, voltando ao detalhe", async () => {
    const user = userEvent.setup();
    const bodies: unknown[] = [];
    routeFetch(fetchMock, {
      "GET /product/3": () => jsonResponse(product),
      "PUT /product/3": ({ body }) => {
        bodies.push(body);
        return jsonResponse({ ...product, quantity: 40 });
      },
    });
    render(<EditProduct productId="3" />);

    const quantity = await screen.findByLabelText("Quantidade");
    await user.clear(quantity);
    await user.type(quantity, "40");
    expect(screen.getByText(QUANTITY_ADJUSTMENT_WARNING)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Salvar alterações" }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/products/3"));
    expect(bodies).toEqual([{ quantity: 40 }]);
  });

  it("404: mostra mensagem apropriada com link para a lista", async () => {
    routeFetch(fetchMock, {
      "GET /product/9": () => apiErrorResponse(404, "Produto não encontrado"),
    });
    render(<EditProduct productId="9" />);

    expect(await screen.findByText("Produto não encontrado")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Voltar para produtos" })).toHaveAttribute(
      "href",
      "/products",
    );
    expect(screen.getByRole("link", { name: /histórico/i })).toHaveAttribute(
      "href",
      "/movements?product_id=9",
    );
    expect(screen.queryByLabelText("SKU")).not.toBeInTheDocument();
  });

  it("id inválido na URL não consulta a API", () => {
    render(<EditProduct productId="abc" />);
    expect(screen.getByText("Produto não encontrado")).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("erro de carregamento oferece 'Tentar novamente'", async () => {
    const user = userEvent.setup();
    let calls = 0;
    routeFetch(fetchMock, {
      "GET /product/3": () => {
        calls += 1;
        return calls === 1
          ? apiErrorResponse(500, "Erro interno do servidor")
          : jsonResponse(product);
      },
    });
    render(<EditProduct productId="3" />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Erro interno do servidor");
    await user.click(screen.getByRole("button", { name: "Tentar novamente" }));
    expect(await screen.findByLabelText("SKU")).toHaveValue("MOUSE-001");
  });
});
