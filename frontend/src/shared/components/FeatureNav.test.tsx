import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { FeatureNav } from './FeatureNav';

function renderEm(caminho: string) {
  return render(
    <MemoryRouter initialEntries={[caminho]}>
      <FeatureNav />
    </MemoryRouter>,
  );
}

describe('FeatureNav', () => {
  it('agrupa os 7 destinos em 3 áreas primárias (Execução/Aprovações/Consultas)', () => {
    renderEm('/execucao');
    const principal = screen.getByRole('navigation', { name: 'Navegação principal' });
    expect(screen.getByRole('link', { name: 'Execução' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Aprovações' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Consultas' })).toBeInTheDocument();
    expect(principal.querySelectorAll('a')).toHaveLength(3);
  });

  it('marca a área ativa com aria-current e mostra o nível secundário dela', () => {
    renderEm('/execucao/pagamentos');

    expect(screen.getByRole('link', { name: 'Execução' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Consultas' })).not.toHaveAttribute('aria-current');

    const secundaria = screen.getByRole('navigation', { name: 'Navegação de Execução' });
    expect(secundaria).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Registrar pagamento' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Registrar liquidação' })).not.toHaveAttribute('aria-current');
  });

  it('não renderiza nível secundário para área com um único destino (Aprovações)', () => {
    renderEm('/execucao/aprovacoes');

    expect(screen.getByRole('link', { name: 'Aprovações' })).toHaveAttribute('aria-current', 'page');
    expect(screen.queryByRole('navigation', { name: 'Navegação de Aprovações' })).not.toBeInTheDocument();
  });

  it('mantém a rota de trilha de liquidação dentro da área Execução, com "Registrar liquidação" ainda corrente (RAZ-143 aninha sob liquidações)', () => {
    renderEm('/execucao/liquidacoes/abc-123/trilha');

    expect(screen.getByRole('link', { name: 'Execução' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Registrar liquidação' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Registrar pagamento' })).not.toHaveAttribute('aria-current');
  });

  it('mantém os 3 destinos de Execução alcançáveis (nenhum link perdido no reagrupamento)', () => {
    renderEm('/execucao');
    expect(screen.getByRole('link', { name: 'Execução orçamentária' })).toHaveAttribute('href', '/execucao');
    expect(screen.getByRole('link', { name: 'Registrar liquidação' })).toHaveAttribute('href', '/execucao/liquidacoes');
    expect(screen.getByRole('link', { name: 'Registrar pagamento' })).toHaveAttribute('href', '/execucao/pagamentos');
  });

  it('mantém os 3 destinos de Consultas alcançáveis (nenhum link perdido no reagrupamento)', () => {
    renderEm('/razao/saldo');
    expect(screen.getByRole('link', { name: 'Saldo por conta' })).toHaveAttribute('href', '/razao/saldo');
    expect(screen.getByRole('link', { name: 'Balancete' })).toHaveAttribute('href', '/razao/balancete');
    expect(screen.getByRole('link', { name: 'Catálogo de contas' })).toHaveAttribute('href', '/razao/contas');
  });

  it('mantém a Fila de aprovação alcançável — área com destino único, sem nível secundário próprio', () => {
    renderEm('/execucao/aprovacoes');
    expect(screen.getByRole('link', { name: 'Aprovações' })).toHaveAttribute('href', '/execucao/aprovacoes');
  });
});
