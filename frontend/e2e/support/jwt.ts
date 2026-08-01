/**
 * Assina JWTs RS256 no formato exigido por `VerificadorJwtGovBr`
 * (plataforma-infra) — mesmo contrato de claims usado por
 * `ServicoIdentidadeGovBrIcpTest` (Java), só que em Node para alimentar o
 * navegador real do Playwright com uma "asserção gov.br" válida sem depender
 * do gov.br staging real (RAZ-211 — esse smoke test fica fora do sandbox,
 * ver docs/operacao/RAZ-126-runbook-oidc-login-govbr.md).
 *
 * O par de chaves é gerado a cada execução (nunca commitado) — a chave
 * pública vai para `GOVBR_IAM_PUBLIC_KEY_PEM` do backend E2E, a privada só
 * vive na memória deste processo de setup.
 */
import { generateKeyPairSync, sign } from 'node:crypto';

export const ISSUER = 'https://e2e.siafic.local/idp';
export const AUDIENCE = 'siafic-e2e';

export type Papel = 'LANCADOR' | 'AUTORIZADOR' | 'PAGADOR' | 'ADMIN_ACESSO' | 'ADMIN_PLATAFORMA' | 'AUDITOR' | 'PUBLICADOR';

export type UsuarioE2e = {
  label: string;
  cpf: string;
  cpfMascarado: string;
  enteId: string;
  papeis: Papel[];
};

export function mascararCpf(cpf: string): string {
  return `***.${cpf.slice(3, 6)}.***-**`;
}

function base64Url(input: Buffer): string {
  return input.toString('base64url');
}

export class AssinadorJwtE2e {
  readonly publicKeyPem: string;
  private readonly privateKeyPem: string;

  constructor() {
    const { publicKey, privateKey } = generateKeyPairSync('rsa', {
      modulusLength: 2048,
      publicKeyEncoding: { type: 'spki', format: 'pem' },
      privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
    });
    this.publicKeyPem = publicKey;
    this.privateKeyPem = privateKey;
  }

  assinar(usuario: UsuarioE2e, nivelConta: 'OURO' | 'PRATA' | 'BRONZE' = 'OURO'): string {
    const agora = Math.floor(Date.now() / 1000);
    const header = base64Url(Buffer.from(JSON.stringify({ alg: 'RS256', typ: 'JWT' }), 'utf8'));
    const payload = base64Url(
      Buffer.from(
        JSON.stringify({
          iss: ISSUER,
          aud: AUDIENCE,
          exp: agora + 3600,
          cpf: usuario.cpf,
          ente_id: usuario.enteId,
          nivel_conta: nivelConta,
        }),
        'utf8',
      ),
    );
    const signingInput = `${header}.${payload}`;
    const assinatura = base64Url(sign('RSA-SHA256', Buffer.from(signingInput, 'ascii'), this.privateKeyPem));
    return `${signingInput}.${assinatura}`;
  }
}
