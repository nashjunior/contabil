import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Modal } from './Modal';

function renderModal(onClose: () => void) {
  return render(
    <Modal titleId="titulo-teste" onClose={onClose}>
      <h2 id="titulo-teste">Título do modal</h2>
      <button type="button">Primeiro</button>
      <button type="button">Último</button>
    </Modal>,
  );
}

describe('Modal', () => {
  it('expõe role="dialog" com aria-modal e aria-labelledby apontando pro título', () => {
    renderModal(vi.fn());
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(dialog).toHaveAccessibleName('Título do modal');
  });

  it('fecha ao pressionar Esc', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    renderModal(onClose);

    await user.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('fecha ao clicar no backdrop, mas não ao clicar dentro do conteúdo', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    renderModal(onClose);

    await user.click(screen.getByRole('dialog'));
    expect(onClose).not.toHaveBeenCalled();

    await user.click(screen.getByRole('dialog').parentElement as HTMLElement);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('prende o Tab dentro do contêiner (do último volta pro primeiro)', async () => {
    const user = userEvent.setup();
    renderModal(vi.fn());

    const primeiro = screen.getByRole('button', { name: 'Primeiro' });
    const ultimo = screen.getByRole('button', { name: 'Último' });
    ultimo.focus();
    expect(ultimo).toHaveFocus();

    await user.tab();
    expect(primeiro).toHaveFocus();
  });
});
