/**
 * Tela minima autenticada (RAZ-120): registra empenho contra a API real
 * (escrita.EmpenhoController) e prova fim-a-fim com DUAS fontes de dado real:
 * (1) GET real de agregado (consulta.ExecucaoConsultaController /orcamentaria);
 * (2) GET real da lista de empenhos registrados (consulta.ExecucaoConsultaController
 * /empenhos, RAZ-121/RAZ-199 — não mais um cache local das respostas de POST desta sessão).
 */
import { useAuth } from '../../../shared/auth/AuthContext';
import { FeatureNav } from '../../../shared/components/FeatureNav';
import { EmpenhoForm } from '../components/EmpenhoForm';
import { ExecucaoList } from '../components/ExecucaoList';
import { ExecucaoOrcamentariaResumo } from '../components/ExecucaoOrcamentariaResumo';
import { useEmpenhosRegistrados } from '../api/useEmpenhosRegistrados';

const AGORA = new Date();
const EXERCICIO_ATUAL = AGORA.getFullYear();
const MES_ATUAL = AGORA.getMonth() + 1;

export function ExecucaoPage() {
  const { sessao, sair } = useAuth();
  const { data } = useEmpenhosRegistrados(EXERCICIO_ATUAL);
  const itens = data?.itens ?? [];

  return (
    <main style={{ maxWidth: 720, margin: '0 auto', padding: 'var(--spacing-xl)' }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 'var(--spacing-lg)' }}>
        <h1>Execução orçamentária</h1>
        {sessao && (
          <p>
            {sessao.enteNome} · CPF {sessao.cpfMascarado}{' '}
            <button type="button" onClick={sair}>
              Sair
            </button>
          </p>
        )}
      </header>

      <FeatureNav />

      <section aria-labelledby="resumo-titulo">
        <h2 id="resumo-titulo">
          Execução do período {String(MES_ATUAL).padStart(2, '0')}/{EXERCICIO_ATUAL}
        </h2>
        <ExecucaoOrcamentariaResumo exercicio={EXERCICIO_ATUAL} mes={MES_ATUAL} />
      </section>

      <section aria-labelledby="registrar-empenho-titulo">
        <h2 id="registrar-empenho-titulo">Registrar empenho</h2>
        <EmpenhoForm />
      </section>

      <section aria-labelledby="lista-execucoes-titulo">
        <h2 id="lista-execucoes-titulo">Empenhos registrados em {EXERCICIO_ATUAL}</h2>
        <ExecucaoList itens={itens} />
      </section>
    </main>
  );
}
