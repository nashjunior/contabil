/**
 * Testa o hook reutilizável isolado de qualquer feature (RAZ-202) — schema barra submit
 * inválido sem chamar `executar`; submit válido mapeia campos→input, chama o caso de uso
 * e reseta o formulário; falha do caso de uso vira erro de root sem perder os valores.
 */
import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { z } from 'zod';
import { useFormDeAgregado } from './useFormDeAgregado';

const schema = z.object({
  nome: z.string().min(1, 'Nome obrigatório'),
});

type Campos = z.infer<typeof schema>;

function FormularioDeTeste({ executar }: { executar: (input: { nomeMaiusculo: string } ) => Promise<string> }) {
  const { form, aoSubmeter } = useFormDeAgregado<Campos, { nomeMaiusculo: string }, string>({
    schema,
    valoresIniciais: { nome: '' },
    paraInput: (campos) => ({ nomeMaiusculo: campos.nome.toUpperCase() }),
    executar,
  });

  return (
    <form onSubmit={aoSubmeter} aria-label="Formulário de teste">
      <label htmlFor="nome">Nome</label>
      <input id="nome" {...form.register('nome')} />
      {form.formState.errors.nome && <span role="alert">{form.formState.errors.nome.message}</span>}
      {form.formState.errors.root?.envio && <span role="alert">{form.formState.errors.root.envio.message}</span>}
      <button type="submit" disabled={form.formState.isSubmitting}>
        Enviar
      </button>
      {form.formState.isSubmitSuccessful && <span>Sucesso.</span>}
    </form>
  );
}

describe('useFormDeAgregado', () => {
  it('barra o submit quando o schema falha e nunca chama o caso de uso', async () => {
    const executar = vi.fn();
    const user = userEvent.setup();
    render(<FormularioDeTeste executar={executar} />);

    await user.click(screen.getByRole('button', { name: 'Enviar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Nome obrigatório');
    expect(executar).not.toHaveBeenCalled();
  });

  it('mapeia campos→input, chama o caso de uso e reseta o formulário no sucesso', async () => {
    const executar = vi.fn().mockResolvedValue('ok');
    const user = userEvent.setup();
    render(<FormularioDeTeste executar={executar} />);

    await user.type(screen.getByLabelText('Nome'), 'ana');
    await user.click(screen.getByRole('button', { name: 'Enviar' }));

    await waitFor(() => expect(screen.getByText('Sucesso.')).toBeInTheDocument());
    expect(executar).toHaveBeenCalledWith({ nomeMaiusculo: 'ANA' });
    expect(screen.getByLabelText('Nome')).toHaveValue('');
  });

  it('vira erro de root quando o caso de uso falha, sem perder os valores digitados', async () => {
    const executar = vi.fn().mockRejectedValue(new Error('Falha ao registrar.'));
    const user = userEvent.setup();
    render(<FormularioDeTeste executar={executar} />);

    await user.type(screen.getByLabelText('Nome'), 'ana');
    await user.click(screen.getByRole('button', { name: 'Enviar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Falha ao registrar.');
    expect(screen.getByLabelText('Nome')).toHaveValue('ana');
  });
});
