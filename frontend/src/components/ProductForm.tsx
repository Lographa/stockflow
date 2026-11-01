"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import type { ChangeEvent, FormEvent } from "react";
import { ApiRequestError } from "@/lib/api";
import {
  EMPTY_PRODUCT_VALUES,
  parseInteger,
  productToFormValues,
  toCreateRequest,
  toUpdateRequest,
  validateProductForm,
} from "@/lib/validation";
import type {
  FieldErrors,
  ProductField,
  ProductFormValues,
} from "@/lib/validation";
import type {
  CreateProductRequest,
  Product,
  UpdateProductRequest,
} from "@/types";
import { Alert } from "./Alert";
import { Button, buttonClasses } from "./Button";
import { Input } from "./Input";

export const QUANTITY_ADJUSTMENT_WARNING =
  "A alteração da quantidade gera automaticamente uma movimentação de ajuste (entrada ou saída pela diferença)";

interface BaseProps {
  /** Destino do link "Cancelar". */
  cancelHref: string;
}

interface CreateProps extends BaseProps {
  mode: "create";
  /** Deve lançar `ApiRequestError` em caso de falha; navega em caso de sucesso. */
  onSubmit: (data: CreateProductRequest) => Promise<unknown>;
}

interface EditProps extends BaseProps {
  mode: "edit";
  product: Product;
  /** Recebe apenas os campos alterados (nunca strings vazias). */
  onSubmit: (data: UpdateProductRequest) => Promise<unknown>;
}

export type ProductFormProps = CreateProps | EditProps;

const KNOWN_FIELDS: readonly ProductField[] = [
  "sku",
  "name",
  "description",
  "price",
  "quantity",
  "min_stock",
];

function isKnownField(key: string): key is ProductField {
  return (KNOWN_FIELDS as readonly string[]).includes(key);
}

/**
 * Formulário reutilizável de produto (cadastro e edição), sem biblioteca de
 * formulários. Validação no cliente espelha o backend; erros `field_errors`
 * da API são exibidos junto aos campos; 409 e demais falhas viram um alerta.
 */
export function ProductForm(props: ProductFormProps) {
  const { mode, cancelHref } = props;
  const product = props.mode === "edit" ? props.product : undefined;

  const [values, setValues] = useState<ProductFormValues>(() =>
    product ? productToFormValues(product) : EMPTY_PRODUCT_VALUES,
  );
  const [errors, setErrors] = useState<FieldErrors<ProductField>>({});
  const [formError, setFormError] = useState<{
    title: string;
    message: string;
  } | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const submittingRef = useRef(false);
  const formRef = useRef<HTMLFormElement>(null);
  const [focusRequest, setFocusRequest] = useState(0);

  // Leva o foco ao primeiro campo inválido após uma tentativa de envio.
  useEffect(() => {
    if (focusRequest === 0) return;
    formRef.current
      ?.querySelector<HTMLElement>('[aria-invalid="true"]')
      ?.focus();
  }, [focusRequest]);

  function handleChange(field: ProductField) {
    return (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      const value = event.target.value;
      setValues((current) => ({ ...current, [field]: value }));
      setErrors((current) => {
        if (!current[field]) return current;
        const next = { ...current };
        delete next[field];
        return next;
      });
    };
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submittingRef.current) return;

    setFormError(null);
    setNotice(null);

    const validation = validateProductForm(values, product);
    if (!validation.parsed) {
      setErrors(validation.errors);
      setFocusRequest((count) => count + 1);
      return;
    }
    setErrors({});

    submittingRef.current = true;
    setSubmitting(true);
    let succeeded = false;
    try {
      if (props.mode === "edit") {
        const changes = toUpdateRequest(validation.parsed, props.product);
        if (Object.keys(changes).length === 0) {
          setNotice("Nenhuma alteração para salvar.");
          return;
        }
        await props.onSubmit(changes);
      } else {
        await props.onSubmit(toCreateRequest(validation.parsed));
      }
      succeeded = true;
    } catch (err) {
      handleSubmitError(err);
    } finally {
      // Em caso de sucesso o botão permanece desabilitado: a página navega
      // para outra rota e um segundo envio criaria/alteraria em duplicidade.
      if (!succeeded) {
        submittingRef.current = false;
        setSubmitting(false);
      }
    }
  }

  function handleSubmitError(err: unknown) {
    if (!(err instanceof ApiRequestError)) {
      setFormError({
        title: "Não foi possível salvar o produto",
        message: "Ocorreu um erro inesperado. Tente novamente.",
      });
      return;
    }

    if (err.status === 400 && err.fieldErrors) {
      const fieldErrors: FieldErrors<ProductField> = {};
      const others: string[] = [];
      for (const [key, message] of Object.entries(err.fieldErrors)) {
        if (isKnownField(key)) fieldErrors[key] = message;
        else others.push(`${key}: ${message}`);
      }
      setErrors(fieldErrors);
      setFocusRequest((count) => count + 1);
      setFormError({
        title: "Corrija os campos indicados",
        message: others.length > 0 ? others.join("; ") : err.message,
      });
      return;
    }

    if (err.status === 409) {
      setFormError({
        title: "SKU ou nome já cadastrado",
        message: `${err.message} O SKU e o nome são únicos, inclusive em relação a produtos excluídos.`,
      });
      return;
    }

    setFormError({
      title: "Não foi possível salvar o produto",
      message: err.message,
    });
  }

  const quantityChanged =
    mode === "edit" &&
    product !== undefined &&
    (() => {
      const parsed = parseInteger(values.quantity, "A quantidade", 0);
      return "value" in parsed && parsed.value !== product.quantity;
    })();

  return (
    <form
      ref={formRef}
      noValidate
      onSubmit={handleSubmit}
      aria-label={mode === "create" ? "Cadastrar produto" : "Editar produto"}
      className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-4 sm:p-6"
    >
      {formError && (
        <Alert variant="error" title={formError.title}>
          {formError.message}
        </Alert>
      )}
      {notice && <Alert variant="info">{notice}</Alert>}

      <div className="grid gap-4 sm:grid-cols-2">
        <Input
          label="SKU"
          name="sku"
          value={values.sku}
          onChange={handleChange("sku")}
          error={errors.sku}
          required
          autoComplete="off"
          disabled={submitting}
        />
        <Input
          label="Nome"
          name="name"
          value={values.name}
          onChange={handleChange("name")}
          error={errors.name}
          required
          autoComplete="off"
          disabled={submitting}
        />
      </div>

      <div className="flex flex-col gap-1">
        <label
          htmlFor="product-description"
          className="text-sm font-medium text-slate-700"
        >
          Descrição
        </label>
        <textarea
          id="product-description"
          name="description"
          rows={3}
          value={values.description}
          onChange={handleChange("description")}
          disabled={submitting}
          aria-invalid={errors.description ? true : undefined}
          aria-describedby={
            errors.description ? "product-description-error" : undefined
          }
          className={`w-full rounded-md border bg-white px-3 py-2 text-sm text-slate-900 focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-indigo-600 disabled:cursor-not-allowed disabled:bg-slate-100 ${
            errors.description ? "border-red-500" : "border-slate-300"
          }`}
        />
        <p className="text-xs text-slate-500">
          Opcional. Até 500 caracteres ({values.description.trim().length}/500).
        </p>
        {errors.description && (
          <p
            id="product-description-error"
            className="text-xs font-medium text-red-600"
          >
            {errors.description}
          </p>
        )}
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <Input
          label="Preço (R$)"
          name="price"
          inputMode="decimal"
          placeholder="0,00"
          value={values.price}
          onChange={handleChange("price")}
          error={errors.price}
          hint="Até 8 dígitos inteiros e 2 decimais."
          required
          disabled={submitting}
        />
        <Input
          label="Quantidade"
          name="quantity"
          inputMode="numeric"
          value={values.quantity}
          onChange={handleChange("quantity")}
          error={errors.quantity}
          hint={
            mode === "create"
              ? "Se maior que zero, gera uma entrada inicial de estoque."
              : "Alterar este valor gera uma movimentação de ajuste."
          }
          required
          disabled={submitting}
        />
        <Input
          label="Estoque mínimo"
          name="min_stock"
          inputMode="numeric"
          value={values.min_stock}
          onChange={handleChange("min_stock")}
          error={errors.min_stock}
          hint="Abaixo deste valor o produto fica com estoque baixo."
          required
          disabled={submitting}
        />
      </div>

      {quantityChanged && (
        <Alert variant="warning">{QUANTITY_ADJUSTMENT_WARNING}</Alert>
      )}

      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Link href={cancelHref} className={buttonClasses("secondary")}>
          Cancelar
        </Link>
        <Button type="submit" loading={submitting}>
          {mode === "create" ? "Cadastrar produto" : "Salvar alterações"}
        </Button>
      </div>
    </form>
  );
}
