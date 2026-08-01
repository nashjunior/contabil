import type { APIRequestContext } from '@playwright/test';
import type { UsuarioRuntime } from './runtime';

/** Chama a API real de execução (não a UI) para os passos do fluxo
 * empenho→liquidação→pagamento→aprovação que ainda não têm tela própria
 * (`frontend/README.md` "Gaps sinalizados" — RAZ-211 cobre o que existe hoje via
 * DOM e o resto via HTTP real contra o mesmo backend, ver spec principal). */
function baseUrl(backendBaseUrl: string, enteId: string): string {
  return `${backendBaseUrl}/api/v1/entes/${enteId}`;
}

function headers(usuario: UsuarioRuntime): Record<string, string> {
  return { Authorization: `Bearer ${usuario.jwt}`, 'Content-Type': 'application/json' };
}

export async function buscarEmpenhoPorHistorico(
  request: APIRequestContext,
  backendBaseUrl: string,
  usuario: UsuarioRuntime,
  exercicio: number,
  historico: string,
): Promise<{ id: string; valor: string }> {
  const resposta = await request.get(`${baseUrl(backendBaseUrl, usuario.enteId)}/execucao/empenhos`, {
    headers: headers(usuario),
    params: { exercicio },
  });
  if (!resposta.ok()) {
    throw new Error(`GET /execucao/empenhos falhou: ${resposta.status()} ${await resposta.text()}`);
  }
  const corpo = (await resposta.json()) as { itens: Array<{ id: string; valor: string; historico: string }> };
  const achado = corpo.itens.find((item) => item.historico === historico);
  if (!achado) {
    throw new Error(`Empenho com histórico "${historico}" não encontrado em GET /execucao/empenhos`);
  }
  return achado;
}

export async function liquidar(
  request: APIRequestContext,
  backendBaseUrl: string,
  usuario: UsuarioRuntime,
  params: { empenhoId: string; valor: string; historico: string },
): Promise<{ id: string; status: string }> {
  const hoje = new Date().toISOString().slice(0, 10);
  const resposta = await request.post(`${baseUrl(backendBaseUrl, usuario.enteId)}/execucao/liquidacoes`, {
    headers: headers(usuario),
    data: {
      empenhoId: params.empenhoId,
      dataCompetencia: hoje,
      valor: params.valor,
      documentosSuporte: [{ tipo: 'nota_fiscal', numero: 'NF-E2E-211', dataEmissao: hoje }],
      historico: params.historico,
    },
  });
  if (resposta.status() !== 201) {
    throw new Error(`POST /execucao/liquidacoes falhou: ${resposta.status()} ${await resposta.text()}`);
  }
  return resposta.json();
}

export async function aprovar(
  request: APIRequestContext,
  backendBaseUrl: string,
  usuario: UsuarioRuntime,
  liquidacaoId: string,
): Promise<{ status: number; corpo: Record<string, unknown> }> {
  const resposta = await request.post(
    `${baseUrl(backendBaseUrl, usuario.enteId)}/execucao/liquidacoes/${liquidacaoId}/aprovacao`,
    { headers: headers(usuario), data: { decisao: 'aprovar' } },
  );
  return { status: resposta.status(), corpo: await resposta.json() };
}

export async function buscarLiquidacaoPorHistorico(
  request: APIRequestContext,
  backendBaseUrl: string,
  usuario: UsuarioRuntime,
  historico: string,
): Promise<{ id: string; statusAprovacao: string }> {
  const resposta = await request.get(`${baseUrl(backendBaseUrl, usuario.enteId)}/execucao/liquidacoes/registradas`, {
    headers: headers(usuario),
  });
  if (!resposta.ok()) {
    throw new Error(`GET /execucao/liquidacoes/registradas falhou: ${resposta.status()} ${await resposta.text()}`);
  }
  const corpo = (await resposta.json()) as {
    itens: Array<{ id: string; historico: string; statusAprovacao: string }>;
  };
  const achado = corpo.itens.find((item) => item.historico === historico);
  if (!achado) {
    throw new Error(`Liquidação com histórico "${historico}" não encontrada em GET /execucao/liquidacoes/registradas`);
  }
  return achado;
}

export async function buscarPagamentoPorHistorico(
  request: APIRequestContext,
  backendBaseUrl: string,
  usuario: UsuarioRuntime,
  historico: string,
): Promise<{ id: string; liquidacaoId: string; valor: string }> {
  const resposta = await request.get(`${baseUrl(backendBaseUrl, usuario.enteId)}/execucao/pagamentos`, {
    headers: headers(usuario),
  });
  if (!resposta.ok()) {
    throw new Error(`GET /execucao/pagamentos falhou: ${resposta.status()} ${await resposta.text()}`);
  }
  const corpo = (await resposta.json()) as {
    itens: Array<{ id: string; liquidacaoId: string; valor: string; historico: string }>;
  };
  const achado = corpo.itens.find((item) => item.historico === historico);
  if (!achado) {
    throw new Error(`Pagamento com histórico "${historico}" não encontrado em GET /execucao/pagamentos`);
  }
  return achado;
}

export async function pagar(
  request: APIRequestContext,
  backendBaseUrl: string,
  usuario: UsuarioRuntime,
  params: { liquidacaoId: string; valor: string; historico: string; idempotencyKey: string },
): Promise<{ id: string; valor: string }> {
  const hoje = new Date().toISOString().slice(0, 10);
  const resposta = await request.post(`${baseUrl(backendBaseUrl, usuario.enteId)}/execucao/pagamentos`, {
    headers: { ...headers(usuario), 'Idempotency-Key': params.idempotencyKey },
    data: {
      liquidacaoId: params.liquidacaoId,
      dataCompetencia: hoje,
      valor: params.valor,
      natureza: 'orcamentario',
      beneficiario: { nome: 'Fornecedor E2E RAZ-211', cpfCnpj: '12345678901' },
      historico: params.historico,
    },
  });
  if (resposta.status() !== 201) {
    throw new Error(`POST /execucao/pagamentos falhou: ${resposta.status()} ${await resposta.text()}`);
  }
  return resposta.json();
}
