/**
 * Cobre o contrato de a11y por nível (design-system-tokens-componentes.md §2, linha 11):
 * danger/warning -> role="alert" (assertivo); info/success -> role="status" (polite).
 */
import '@testing-library/jest-dom/vitest';
import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Alert, type AlertLevel } from '../Alert';

function renderAlert(level: AlertLevel) {
  render(
    <Alert level={level}>
      <Alert.Title>Título</Alert.Title>
      <Alert.Body>Corpo da mensagem.</Alert.Body>
    </Alert>,
  );
}

describe.each<[AlertLevel, 'alert' | 'status']>([
  ['danger', 'alert'],
  ['warning', 'alert'],
  ['info', 'status'],
  ['success', 'status'],
])('Alert nível=%s', (level, expectedRole) => {
  it(`usa role="${expectedRole}" e renderiza título/corpo`, () => {
    renderAlert(level);

    expect(screen.getByRole(expectedRole)).toBeInTheDocument();
    expect(screen.getByText('Título')).toBeInTheDocument();
    expect(screen.getByText('Corpo da mensagem.')).toBeInTheDocument();
  });
});

it('não expõe role="alert" para níveis info/success (evita interromper leitor de tela à toa)', () => {
  renderAlert('info');
  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});
