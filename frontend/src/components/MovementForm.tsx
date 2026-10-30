"use client";

import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { ApiRequestError, createMovement } from "@/lib/api";
import { validateMovementForm } from "@/lib/validation";
import type {
  FieldErrors,
  MovementField,
  MovementFormValues,
} from "@/lib/validation";
import type { Movement, MovementType } from "@/types";
import { Alert } from "./Alert";
import { Button } from "./Button";
import { Input } from "./Input";
import { Select } from "./Select";

interface MovementFormProps {
  productId: number;
  /** Chamado após o POST /movements ser aceito (201): recarregue produto e histórico. */
  onCreated: (movement: Movement) => void;
}

const INITIAL_VALUES: MovementFormValues = {
  type: "ENTRY",
  quantity: "",
  description: "",
};

const KNOWN_FIELDS: readonly MovementField[] = [
  "type",
  "quantity",
  "description",
];

function isKnownField(key: string): key is MovementField {
  return (KNOWN_FIELDS as readonly string[]).includes(key);
}

/**
 * Registra entrada/saída de estoque. O cliente nunca calcula nem envia saldo:
 * quem decide (e valida "saída ≤ saldo") é o backend; em 409 apenas exibimos
 * a mensagem da API e não alteramos nenhum dado da tela.
 */
export function MovementForm({ productId, onCreated }: MovementFormProps) {
  const [values, setValues] = useState<MovementFormValues>(INITIAL_VALUES);
  const [errors, setErrors] = useState<FieldErrors<MovementField>>({});
  const [formError, setFormError] = useState<{
    title: string;
    message: string;
  } | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const submittingRef = useRef(false);
  const formRef = useRef<HTMLFormElement>(null);
  const [focusRequest, setFocusRequest] = useState(0);

  useEffect(() => {
    if (focusRequest === 0) return;
    formRef.current
      ?.querySelector<HTMLElement>('[aria-invalid="true"]')
      ?.focus();
  }, [focusRequest]);

  function setField<K extends MovementField>(
    field: K,
    value: MovementFormValues[K],
  ) {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => {
      if (!current[field]) return current;
      const next = { ...current };
      delete next[field];
      return next;
    });
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submittingRef.current) return;

    setFormError(null);
    setSuccess(null);

    const validation = validateMovementForm(values);
    if (!validation.request) {
      setErrors(validation.errors);
      setFocusRequest((count) => count + 1);
      return;
    }
    setErrors({});

    submittingRef.current = true;
    setSubmitting(true);
    try {
      const movement = await createMovement({
        product_id: productId,
        ...validation.request,
      });
      setValues((current) => ({ ...current, quantity: "", description: "" }));
      const label = movement.type === "ENTRY" ? "Entrada" : "Saída";
      setSuccess(
        movement.stock_after !== null && movement.stock_after !== undefined
          ? `${label} de ${movement.quantity} un. registrada. Saldo após a operação: ${movement.stock_after}.`
          : `${label} de ${movement.quantity} un. registrada.`,
      );
      onCreated(movement);
    } catch (err) {
      handleError(err);
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  }

  function handleError(err: unknown) {
    if (!(err instanceof ApiRequestError)) {
      setFormError({
        title: "Não foi possível registrar a movimentação",
        message: "Ocorreu um erro inesperado. Tente novamente.",
      });
      return;
    }

    if (err.status === 400 && err.fieldErrors) {
      const fieldErrors: FieldErrors<MovementField> = {};
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

    setFormError({
      title:
        err.status === 409
          ? "Movimentação não realizada"
          : "Não foi possível registrar a movimentação",
      message: err.message,
    });
  }

  return (
    <form
      ref={formRef}
      noValidate
      onSubmit={handleSubmit}
      aria-label="Registrar movimentação"
      className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-4"
    >
      <div className="flex flex-col gap-2 empty:hidden">
        {formError && (
          <Alert variant="error" title={formError.title}>
            {formError.message}
          </Alert>
        )}
        {success && <Alert variant="success">{success}</Alert>}
      </div>

      <div className="grid gap-4 sm:grid-cols-[10rem_10rem_1fr]">
        <Select
          label="Tipo"
          name="type"
          value={values.type}
          onChange={(event) =>
            setField("type", event.target.value as MovementType)
          }
          error={errors.type}
          disabled={submitting}
        >
          <option value="ENTRY">Entrada</option>
          <option value="EXIT">Saída</option>
        </Select>
        <Input
          label="Quantidade"
          name="quantity"
          inputMode="numeric"
          value={values.quantity}
          onChange={(event) => setField("quantity", event.target.value)}
          error={errors.quantity}
          required
          disabled={submitting}
        />
        <Input
          label="Descrição (opcional)"
          name="description"
          value={values.description}
          onChange={(event) => setField("description", event.target.value)}
          error={errors.description}
          hint="Até 255 caracteres."
          autoComplete="off"
          disabled={submitting}
        />
      </div>

      <div className="flex justify-end">
        <Button type="submit" loading={submitting}>
          Registrar movimentação
        </Button>
      </div>
    </form>
  );
}
