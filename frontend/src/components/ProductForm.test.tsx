import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ApiRequestError } from "@/lib/api";
import { makeProduct } from "@/test/fixtures";
import { ProductForm, QUANTITY_ADJUSTMENT_WARNING } from "./ProductForm";

function apiError(
  status: number,
  message: string,
  fieldErrors?: Record<string, string>,
) {
  return new ApiRequestError({ status, error: "Erro", message, fieldErrors });
}

async function fillCreateForm(
  user: ReturnType<typeof userEvent.setup>,
  overrides: Partial<Record<"sku" | "name" | "price" | "quantity" | "min", string>> = {},
) {
  const values = {
    sku: "MOUSE-001",
    name: "Mouse Gamer",
    price: "129,90",
    quantity: "25",
    min: "10",
    ...overrides,
  };
  await user.type(screen.getByLabelText("SKU"), values.sku);
  await user.type(screen.getByLabelText("Nome"), values.name);
  await user.type(screen.getByLabelText(/Preço/), values.price);
  const quantity = screen.getByLabelText("Quantidade");
  await user.clear(quantity);
  await user.type(quantity, values.quantity);
  const min = screen.getByLabelText("Estoque mínimo");
  await user.clear(min);
  await user.type(min, values.min);
}

describe("ProductForm (cadastro)", () => {
  it("mostra erros de validação sem chamar a API e foca o primeiro campo inválido", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    render(<ProductForm mode="create" onSubmit={onSubmit} cancelHref="/products" />);

    await user.click(screen.getByRole("button", { name: "Cadastrar produto" }));

    expect(screen.getByText("O SKU é obrigatório")).toBeInTheDocument();
    expect(screen.getByText("O nome é obrigatório")).toBeInTheDocument();
    expect(screen.getByText("O preço é obrigatório")).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByLabelText("SKU")).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByLabelText("SKU")).toHaveFocus();
  });

  it.each([
    ["preço negativo", { price: "-1" }, "O preço não pode ser negativo"],
    ["preço com 3 decimais", { price: "1,999" }, /2 casas decimais/],
    ["preço com 9 dígitos inteiros", { price: "123456789" }, /deve ter no máximo 8 dígitos inteiros/],
    ["quantidade negativa", { quantity: "-1" }, "A quantidade deve ser maior ou igual a zero"],
    ["quantidade decimal", { quantity: "1,5" }, "A quantidade deve ser um número inteiro"],
    ["estoque mínimo negativo", { min: "-2" }, "O estoque mínimo deve ser maior ou igual a zero"],
    ["sku com 51 caracteres", { sku: "a".repeat(51) }, /50 caracteres/],
    ["nome com 151 caracteres", { name: "a".repeat(151) }, /150 caracteres/],
  ])("rejeita %s", async (_label, overrides, message) => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    render(<ProductForm mode="create" onSubmit={onSubmit} cancelHref="/products" />);

    await fillCreateForm(user, overrides);
    await user.click(screen.getByRole("button", { name: "Cadastrar produto" }));

    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("rejeita descrição acima de 500 caracteres", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    render(<ProductForm mode="create" onSubmit={onSubmit} cancelHref="/products" />);

    await fillCreateForm(user);
    await user.click(screen.getByLabelText("Descrição"));
    await user.paste("a".repeat(501));
    await user.click(screen.getByRole("button", { name: "Cadastrar produto" }));

    expect(await screen.findByText(/no máximo 500 caracteres/)).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("envia o payload convertido (preço numérico, descrição vazia omitida)", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<ProductForm mode="create" onSubmit={onSubmit} cancelHref="/products" />);

    await fillCreateForm(user);
    await user.click(screen.getByRole("button", { name: "Cadastrar produto" }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit).toHaveBeenCalledWith({
      sku: "MOUSE-001",
      name: "Mouse Gamer",
      price: 129.9,
      quantity: 25,
      min_stock: 10,
    });
  });

  it("exibe field_errors da API junto aos campos", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockRejectedValue(
      apiError(400, "Validação falhou", {
        sku: "SKU inválido pelo servidor",
        price: "O preço não pode ser negativo",
        min_stock: "Estoque mínimo inválido",
      }),
    );
    render(<ProductForm mode="create" onSubmit={onSubmit} cancelHref="/products" />);

    await fillCreateForm(user);
    await user.click(screen.getByRole("button", { name: "Cadastrar produto" }));

    expect(await screen.findByText("SKU inválido pelo servidor")).toBeInTheDocument();
    expect(screen.getByText("O preço não pode ser negativo")).toBeInTheDocument();
    expect(screen.getByText("Estoque mínimo inválido")).toBeInTheDocument();
    expect(screen.getByLabelText("SKU")).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByLabelText("SKU")).toHaveAccessibleDescription(
      /SKU inválido pelo servidor/,
    );
    expect(screen.getByRole("alert")).toHaveTextContent("Corrija os campos indicados");
    // botão reabilitado para nova tentativa
    expect(screen.getByRole("button", { name: "Cadastrar produto" })).toBeEnabled();
  });

  it("limpa o erro de um campo ao editá-lo", async () => {
    const user = userEvent.setup();
    render(<ProductForm mode="create" onSubmit={vi.fn()} cancelHref="/products" />);

    await user.click(screen.getByRole("button", { name: "Cadastrar produto" }));
    expect(screen.getByText("O SKU é obrigatório")).toBeInTheDocument();

    await user.type(screen.getByLabelText("SKU"), "X");
    expect(screen.queryByText("O SKU é obrigatório")).not.toBeInTheDocument();
  });

  it("trata 409 (SKU/nome já cadastrado) com mensagem clara", async () => {
    const user = userEvent.setup();
    const onSubmit = vi
      .fn()
      .mockRejectedValue(apiError(409, "Já existe um produto com o SKU MOUSE-001."));
    render(<ProductForm mode="create" onSubmit={onSubmit} cancelHref="/products" />);

    await fillCreateForm(user);
    await user.click(screen.getByRole("button", { name: "Cadastrar produto" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("SKU ou nome já cadastrado");
    expect(alert).toHaveTextContent("Já existe um produto com o SKU MOUSE-001.");
    expect(alert).toHaveTextContent("inclusive em relação a produtos excluídos");
    expect(screen.getByRole("button", { name: "Cadastrar produto" })).toBeEnabled();
  });

  it("mostra a mensagem da API em falhas genéricas (ex.: rede)", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockRejectedValue(
      new ApiRequestError({ status: 0, error: "Erro de rede", message: "Sem conexão com o servidor." }),
    );
    render(<ProductForm mode="create" onSubmit={onSubmit} cancelHref="/products" />);

    await fillCreateForm(user);
    await user.click(screen.getByRole("button", { name: "Cadastrar produto" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Sem conexão com o servidor.");
  });

  it("envia uma única vez: desabilita o botão durante o envio e ignora cliques repetidos", async () => {
    const user = userEvent.setup();
    let resolveSubmit!: () => void;
    const onSubmit = vi.fn(
      () => new Promise<void>((resolve) => (resolveSubmit = resolve)),
    );
    render(<ProductForm mode="create" onSubmit={onSubmit} cancelHref="/products" />);

    await fillCreateForm(user);
    const button = screen.getByRole("button", { name: "Cadastrar produto" });
    await user.click(button);
    await user.click(button);
    await user.keyboard("{Enter}");

    expect(onSubmit).toHaveBeenCalledTimes(1);
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute("aria-busy", "true");

    resolveSubmit();
    // após sucesso o botão continua desabilitado (a página navega)
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(button).toBeDisabled();
  });

  it("usa Cancelar como link para a lista", () => {
    render(<ProductForm mode="create" onSubmit={vi.fn()} cancelHref="/products" />);
    expect(screen.getByRole("link", { name: "Cancelar" })).toHaveAttribute("href", "/products");
  });
});

describe("ProductForm (edição)", () => {
  const product = makeProduct({ description: "Mouse com fio" });

  it("pré-carrega os valores do produto", () => {
    render(
      <ProductForm mode="edit" product={product} onSubmit={vi.fn()} cancelHref="/products/1" />,
    );
    expect(screen.getByLabelText("SKU")).toHaveValue("MOUSE-001");
    expect(screen.getByLabelText("Nome")).toHaveValue("Mouse Gamer");
    expect(screen.getByLabelText("Descrição")).toHaveValue("Mouse com fio");
    expect(screen.getByLabelText(/Preço/)).toHaveValue("129,90");
    expect(screen.getByLabelText("Quantidade")).toHaveValue("25");
    expect(screen.getByLabelText("Estoque mínimo")).toHaveValue("10");
  });

  it("avisa que alterar a quantidade gera movimentação de ajuste (somente quando muda)", async () => {
    const user = userEvent.setup();
    render(
      <ProductForm mode="edit" product={product} onSubmit={vi.fn()} cancelHref="/products/1" />,
    );

    expect(screen.queryByText(QUANTITY_ADJUSTMENT_WARNING)).not.toBeInTheDocument();

    const quantity = screen.getByLabelText("Quantidade");
    await user.clear(quantity);
    await user.type(quantity, "30");
    expect(screen.getByText(QUANTITY_ADJUSTMENT_WARNING)).toBeInTheDocument();
    expect(QUANTITY_ADJUSTMENT_WARNING).toBe(
      "A alteração da quantidade gera automaticamente uma movimentação de ajuste (entrada ou saída pela diferença)",
    );

    await user.clear(quantity);
    await user.type(quantity, "25");
    expect(screen.queryByText(QUANTITY_ADJUSTMENT_WARNING)).not.toBeInTheDocument();
  });

  it("envia apenas os campos alterados", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(
      <ProductForm mode="edit" product={product} onSubmit={onSubmit} cancelHref="/products/1" />,
    );

    const quantity = screen.getByLabelText("Quantidade");
    await user.clear(quantity);
    await user.type(quantity, "30");
    await user.click(screen.getByRole("button", { name: "Salvar alterações" }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit).toHaveBeenCalledWith({ quantity: 30 });
  });

  it("não envia quando nada mudou", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    render(
      <ProductForm mode="edit" product={product} onSubmit={onSubmit} cancelHref="/products/1" />,
    );

    await user.click(screen.getByRole("button", { name: "Salvar alterações" }));

    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByText("Nenhuma alteração para salvar.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Salvar alterações" })).toBeEnabled();
  });

  it("não permite esvaziar a descrição existente (a API rejeita string vazia)", async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    render(
      <ProductForm mode="edit" product={product} onSubmit={onSubmit} cancelHref="/products/1" />,
    );

    await user.clear(screen.getByLabelText("Descrição"));
    await user.click(screen.getByRole("button", { name: "Salvar alterações" }));

    expect(await screen.findByText(/não pode ficar vazia/)).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("trata 409 ao editar", async () => {
    const user = userEvent.setup();
    const onSubmit = vi
      .fn()
      .mockRejectedValue(apiError(409, "Já existe um produto com o nome Teclado."));
    render(
      <ProductForm mode="edit" product={product} onSubmit={onSubmit} cancelHref="/products/1" />,
    );

    const name = screen.getByLabelText("Nome");
    await user.clear(name);
    await user.type(name, "Teclado");
    await user.click(screen.getByRole("button", { name: "Salvar alterações" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Já existe um produto com o nome Teclado.");
  });
});
