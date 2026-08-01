export async function aguardarHttp200(url: string, timeoutMs: number, intervaloMs = 500): Promise<void> {
  const prazo = Date.now() + timeoutMs;
  let ultimoErro: unknown;
  while (Date.now() < prazo) {
    try {
      const resposta = await fetch(url);
      if (resposta.ok) return;
      ultimoErro = new Error(`HTTP ${resposta.status}`);
    } catch (erro) {
      ultimoErro = erro;
    }
    await new Promise((resolve) => setTimeout(resolve, intervaloMs));
  }
  throw new Error(`Timeout esperando ${url} responder OK: ${String(ultimoErro)}`);
}
