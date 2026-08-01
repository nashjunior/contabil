import type { Preview } from '@storybook/react-vite'

import '../src/tokens/theme.css';

const preview: Preview = {
  parameters: {
    controls: {
      matchers: {
       color: /(background|color)$/i,
       date: /Date$/i,
      },
    },
    // guardiao-frontend exige acessibilidade eMAG/WCAG AA — violação de a11y
    // quebra a story (erro), nunca só warning.
    a11y: {
      test: 'error',
    },
  },
};

export default preview;