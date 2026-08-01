import { mascararCpf, type Papel, type UsuarioE2e } from './jwt';

/** Mesmos UUIDs de `ENTES_DEV` (`shared/auth/LoginPage.tsx`) — dois entes reais
 * para provar isolamento multi-tenant (RAZ-211) via dois usuários distintos. */
export const ENTE_A = '11111111-1111-4111-8111-111111111111';
export const ENTE_B = '22222222-2222-4222-8222-222222222222';

function usuario(label: string, cpf: string, enteId: string, papeis: Papel[]): UsuarioE2e {
  return { label, cpf, cpfMascarado: mascararCpf(cpf), enteId, papeis };
}

/** Segregação de funções (Regra 9 / ADR-0023): quem lança não pode aprovar a
 * própria liquidação — por isso um CPF de lançador e outro de autorizador por ente,
 * mesmo padrão de `ExecucaoEscritaHttpIntegrationTest` (CPF_ORDENADOR/CPF_APROVADOR).
 * AUDITOR entra em ambos porque é o ÚNICO papel que concede `Acao.LER`
 * (`IamProperties.Papel.permite`) — sem ele nenhuma consulta (GET) passa pelo RBAC
 * real, só pela escrita; o duplo de teste da JVM (`ServicoIdentidadeDeTeste`, que
 * sempre autoriza) mascarava essa exigência. */
export const USUARIOS = {
  enteALancador: usuario('ente-a-lancador', '11122233344', ENTE_A, ['LANCADOR', 'PAGADOR', 'AUDITOR']),
  enteAAutorizador: usuario('ente-a-autorizador', '55566677788', ENTE_A, ['AUTORIZADOR', 'AUDITOR']),
  enteBLancador: usuario('ente-b-lancador', '99988877766', ENTE_B, ['LANCADOR', 'PAGADOR', 'AUDITOR']),
  enteBAutorizador: usuario('ente-b-autorizador', '44433322211', ENTE_B, ['AUTORIZADOR', 'AUDITOR']),
} as const;

/** Contas PCASP mínimas para o roteiro contábil de empenho→liquidação→pagamento
 * fechar (mesma lista de `ExecucaoEscritaHttpIntegrationTest.contasPcaspNecessarias`). */
export const CONTAS_PCASP: Array<[codigo: string, descricao: string, natureza: string]> = [
  ['6.2.2.1.1', 'Credito Disponivel', 'orcamentaria'],
  ['6.2.2.1.3', 'Credito Empenhado a Liquidar', 'orcamentaria'],
  ['6.2.2.1.4', 'Empenhado Liquidado a Pagar', 'orcamentaria'],
  ['6.2.2.1.5', 'Empenhado Pago', 'orcamentaria'],
  ['3.3.3.1.01', 'VPD de servicos', 'patrimonial'],
  ['2.1.3', 'Fornecedores a Pagar', 'patrimonial'],
  ['1.1.1', 'Caixa e Bancos', 'patrimonial'],
];
