/**
 * Fábrica de value objects de identidade tipados por UUID — mirror funcional dos
 * `record XxxId(UUID valor)` do backend (`execucao-domain`, ex.: `DotacaoId.java`).
 * Uma fábrica genérica evita repetir o mesmo regex/branding por VO (DotacaoId,
 * CredorId, UnidadeGestoraId, ContratoId — ver `features/execucao/domain/`).
 *
 * Framework-free: zero import de React/RHF/zod. É consumido tanto pelo domínio
 * (mapeamento para o input do caso de uso) quanto, indiretamente, pelo schema de
 * validação (`empenhoSchema.ts`), que reusa `isValid` em vez de reimplementar o
 * regex — fonte única de verdade (RAZ-202).
 */
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

declare const UuidVOBrand: unique symbol;
export type UuidVO<Marca extends string> = string & { readonly [UuidVOBrand]: Marca };

export type TipoIdVO<Marca extends string> = {
  isValid: (valor: string) => valor is UuidVO<Marca>;
  parse: (valor: string) => UuidVO<Marca>;
};

export function criarTipoIdVO<Marca extends string>(rotulo: string): TipoIdVO<Marca> {
  function isValid(valor: string): valor is UuidVO<Marca> {
    return UUID_PATTERN.test(valor.trim());
  }

  function parse(valor: string): UuidVO<Marca> {
    if (!isValid(valor)) {
      throw new Error(`${rotulo}: UUID inválido ("${valor}")`);
    }
    return valor.trim() as UuidVO<Marca>;
  }

  return { isValid, parse };
}
