import type { Meta, StoryObj } from '@storybook/react-vite';
import { Alert } from './Alert';
import { FIGMA_DESIGN_MAP } from '../../figma-map';

const meta: Meta<typeof Alert> = {
  title: 'Componentes/Alert',
  component: Alert,
  tags: ['autodocs'],
  parameters: {
    design: { type: 'figma', url: FIGMA_DESIGN_MAP.Alert.url },
  },
};
export default meta;

type Story = StoryObj<typeof Alert>;

export const Danger: Story = {
  render: () => (
    <Alert level="danger">
      <Alert.Title>Aviso crítico</Alert.Title>
      <Alert.Body>Descrição da mensagem crítica — texto livre por instância (ex.: lacuna de API, erro bloqueante).</Alert.Body>
    </Alert>
  ),
};

export const Warning: Story = {
  render: () => (
    <Alert level="warning">
      <Alert.Title>Atenção</Alert.Title>
      <Alert.Body>Nota de atenção — uso inline, menor severidade que o nível crítico.</Alert.Body>
    </Alert>
  ),
};

export const Info: Story = {
  render: () => (
    <Alert level="info">
      <Alert.Title>Informativo</Alert.Title>
      <Alert.Body>Nota informativa inline — contexto adicional, sem indicar erro ou risco.</Alert.Body>
    </Alert>
  ),
};

export const Success: Story = {
  render: () => (
    <Alert level="success">
      <Alert.Title>Sucesso</Alert.Title>
      <Alert.Body>Confirmação de que a operação foi concluída com sucesso.</Alert.Body>
    </Alert>
  ),
};
