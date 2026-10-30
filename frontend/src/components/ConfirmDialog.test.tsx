import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ConfirmDialog } from "./ConfirmDialog";

function setup(props: Partial<React.ComponentProps<typeof ConfirmDialog>> = {}) {
  const onConfirm = vi.fn();
  const onCancel = vi.fn();
  render(
    <ConfirmDialog
      open
      title="Excluir?"
      confirmLabel="Sim, excluir"
      onConfirm={onConfirm}
      onCancel={onCancel}
      {...props}
    >
      Texto de confirmação
    </ConfirmDialog>,
  );
  return { onConfirm, onCancel };
}

describe("ConfirmDialog", () => {
  it("não renderiza fechado", () => {
    render(
      <ConfirmDialog open={false} title="X" onConfirm={vi.fn()} onCancel={vi.fn()}>
        corpo
      </ConfirmDialog>,
    );
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("tem rótulo e descrição acessíveis e foca o botão seguro", () => {
    setup();
    const dialog = screen.getByRole("alertdialog", { name: "Excluir?" });
    expect(dialog).toHaveAccessibleDescription("Texto de confirmação");
    expect(screen.getByRole("button", { name: "Cancelar" })).toHaveFocus();
  });

  it("confirma e cancela pelos botões", async () => {
    const user = userEvent.setup();
    const { onConfirm, onCancel } = setup();

    await user.click(screen.getByRole("button", { name: "Sim, excluir" }));
    expect(onConfirm).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole("button", { name: "Cancelar" }));
    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  it("mantém o foco preso com Tab / Shift+Tab", async () => {
    const user = userEvent.setup();
    setup();
    const cancel = screen.getByRole("button", { name: "Cancelar" });
    const confirm = screen.getByRole("button", { name: "Sim, excluir" });

    await user.tab();
    expect(confirm).toHaveFocus();
    await user.tab();
    expect(cancel).toHaveFocus();
    await user.tab({ shift: true });
    expect(confirm).toHaveFocus();
  });

  it("ocupado: desabilita cancelar e ignora Esc", async () => {
    const user = userEvent.setup();
    const { onCancel } = setup({ busy: true });

    expect(screen.getByRole("button", { name: "Cancelar" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Sim, excluir" })).toBeDisabled();
    await user.keyboard("{Escape}");
    expect(onCancel).not.toHaveBeenCalled();
  });
});
