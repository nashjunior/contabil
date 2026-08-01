/**
 * Mapa componente -> frame Figma (RAZ-195/RAZ-214).
 *
 * Code Connect oficial (`add_code_connect_map`) exige plano Figma Organization/
 * Enterprise — a conta só tem Pro (ver docs/arquitetura-tecnica/
 * design-system-tokens-componentes.md §3). Este mapa é o substituto adotado:
 * alimenta `parameters.design` (addon-designs) nas stories, com link direto
 * pro frame no Figma. Fonte única — as stories importam daqui em vez de
 * hardcodar URL, e o script `scripts/check-design-links.mjs` valida cobertura
 * contra este mapa.
 *
 * Tabela completa (20 componentes do design system, só 4 "prontos para
 * mapear" hoje porque só Alert/FormSection/Select têm contraparte em código)
 * está em docs/arquitetura-tecnica/design-system-tokens-componentes.md §4.
 */
const FIGMA_FILE_KEY = 'ObQu8oMQ0cEGbONMXgpuLU';

export type FigmaDesignRef = {
  nodeId: string;
  url: string;
};

function figmaUrl(nodeId: string): string {
  return `https://www.figma.com/design/${FIGMA_FILE_KEY}/SIAFIC-Design-System?node-id=${nodeId.replace(':', '-')}`;
}

export const FIGMA_DESIGN_MAP = {
  'FormSection.Field': { nodeId: '8:19', url: figmaUrl('8:19') },
  'Select.AsyncPanel': { nodeId: '96:1365', url: figmaUrl('96:1365') },
  'Select.Multiple': { nodeId: '96:1440', url: figmaUrl('96:1440') },
  Alert: { nodeId: '49:6', url: figmaUrl('49:6') },
} as const satisfies Record<string, FigmaDesignRef>;
