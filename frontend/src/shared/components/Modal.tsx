/**
 * Modal mínimo local (ADR-0055) — nenhum componente de Modal/Dialog existe ainda em
 * `@siafic/design-system` (só `Alert`/`FormSection`/`Select`); a peça equivalente já existe
 * pronta pra reuso no Figma (Simple Design System, componente "Dialog"), mas trocar o design
 * system por esse componente é follow-up de higiene do Figma, não bloqueia esta implementação.
 *
 * `role="dialog"` `aria-modal`, foco move pro contêiner ao abrir, Tab preso entre os elementos
 * focáveis encontrados no contêiner (recalculado a cada Tab — o conteúdo pode mudar, ex.:
 * textarea de motivo aparecendo), Esc e clique no backdrop fecham. Sem token de cor dedicado
 * pro scrim ainda (`--color-neutral-900` cobre `bg/overlay-scrim` no Figma mas não foi
 * materializado como variável própria) — usa o hex direto com alpha.
 */
import { useEffect, useRef, type ReactNode } from 'react';

const SELETOR_FOCAVEL =
  'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

export type ModalProps = {
  titleId: string;
  onClose: () => void;
  children: ReactNode;
};

export function Modal({ titleId, onClose, children }: ModalProps) {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    containerRef.current?.focus();

    function aoTeclar(evento: KeyboardEvent) {
      if (evento.key === 'Escape') {
        onClose();
        return;
      }
      if (evento.key !== 'Tab' || !containerRef.current) return;

      const focaveis = Array.from(containerRef.current.querySelectorAll<HTMLElement>(SELETOR_FOCAVEL));
      if (focaveis.length === 0) return;
      const primeiro = focaveis[0];
      const ultimo = focaveis[focaveis.length - 1];

      if (evento.shiftKey && document.activeElement === primeiro) {
        evento.preventDefault();
        ultimo.focus();
      } else if (!evento.shiftKey && document.activeElement === ultimo) {
        evento.preventDefault();
        primeiro.focus();
      }
    }

    document.addEventListener('keydown', aoTeclar);
    return () => document.removeEventListener('keydown', aoTeclar);
  }, [onClose]);

  return (
    <div
      onMouseDown={(evento) => {
        if (evento.target === evento.currentTarget) onClose();
      }}
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(15, 23, 42, 0.5)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 100,
        padding: 'var(--spacing-lg)',
      }}
    >
      <div
        ref={containerRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        style={{
          background: 'var(--color-bg-surface)',
          borderRadius: 'var(--radius-lg)',
          boxShadow: 'var(--shadow-overlay)',
          padding: 'var(--spacing-xl)',
          maxWidth: 480,
          width: '100%',
          maxHeight: '90vh',
          overflowY: 'auto',
        }}
      >
        {children}
      </div>
    </div>
  );
}
