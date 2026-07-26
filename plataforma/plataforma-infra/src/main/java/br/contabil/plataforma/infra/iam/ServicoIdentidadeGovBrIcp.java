package br.contabil.plataforma.infra.iam;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import br.contabil.plataforma.domain.TenantId;
import br.contabil.plataforma.domain.iam.ServicoIdentidade;

final class ServicoIdentidadeGovBrIcp implements ServicoIdentidade {

    private final VerificadorJwtGovBr verificadorGovBr;
    private final VerificadorCertificadoIcp verificadorIcp;
    private final IamProperties properties;
    private final Clock clock;

    ServicoIdentidadeGovBrIcp(
            VerificadorJwtGovBr verificadorGovBr,
            VerificadorCertificadoIcp verificadorIcp,
            IamProperties properties,
            Clock clock) {
        this.verificadorGovBr = Objects.requireNonNull(verificadorGovBr, "verificadorGovBr");
        this.verificadorIcp = Objects.requireNonNull(verificadorIcp, "verificadorIcp");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Sessao autenticar(Credencial credencial) {
        Objects.requireNonNull(credencial, "credencial");
        IdentidadeVerificada identidade = switch (credencial) {
            case CredencialGovBr(String assercao) -> verificadorGovBr.verificar(assercao);
            case CredencialCertificadoIcp(String cadeiaCertificadoPem) -> verificadorIcp.verificar(cadeiaCertificadoPem);
        };
        IamProperties.Concessao concessao = concessaoPara(identidade);
        return new Sessao(
                UUID.randomUUID(),
                new Cpf(identidade.cpf()),
                TenantId.de(concessao.enteId().toString()),
                concessao.orgaoOptional(),
                identidade.mfaForteConcluido(),
                expiraEm(identidade.expiraEm()));
    }

    @Override
    public boolean autorizar(Sessao sessao, Recurso recurso, Acao acao) {
        Objects.requireNonNull(sessao, "sessao");
        Objects.requireNonNull(recurso, "recurso");
        Objects.requireNonNull(acao, "acao");
        if (sessao.expiraEm().isBefore(Instant.now(clock))) {
            return false;
        }
        return properties.concessoes().stream()
                .filter(concessao -> concessao.cpf().equals(sessao.titular().numero()))
                .filter(concessao -> concessao.enteId().equals(sessao.ente().valor()))
                .filter(concessao -> concessao.orgaoOptional().equals(sessao.orgao()))
                .flatMap(concessao -> concessao.papeis().stream())
                .anyMatch(papel -> papel.permite(recurso, acao));
    }

    @Override
    public Sessao completarMfa(DesafioMfa desafio, RespostaMfa resposta) {
        Objects.requireNonNull(desafio, "desafio");
        Objects.requireNonNull(resposta, "resposta");
        throw new NaoAutenticadoException(
                "MFA local nao e aceito; reautentique com gov.br Prata/Ouro ou certificado ICP-Brasil");
    }

    private IamProperties.Concessao concessaoPara(IdentidadeVerificada identidade) {
        List<IamProperties.Concessao> candidatas = properties.concessoes().stream()
                .filter(concessao -> concessao.cpf().equals(identidade.cpf()))
                .filter(concessao -> identidade.enteId() == null || concessao.enteId().equals(identidade.enteId()))
                .filter(concessao -> identidade.orgao() == null
                        || concessao.orgaoOptional().isEmpty()
                        || concessao.orgaoOptional().orElseThrow().equals(identidade.orgao()))
                .toList();
        if (candidatas.isEmpty()) {
            throw new SemPermissaoException("nenhuma concessao RBAC para " + IamProperties.mascararCpf(identidade.cpf()));
        }
        if (candidatas.size() > 1 && identidade.enteId() == null) {
            throw new SemPermissaoException(
                    "certificado ICP-Brasil sem ente resolvido para CPF com multiplas concessoes");
        }
        return candidatas.getFirst();
    }

    private Instant expiraEm(Instant expiraCredencial) {
        Instant ttlSessao = Instant.now(clock).plus(properties.sessionTtl());
        return expiraCredencial == null || expiraCredencial.isAfter(ttlSessao) ? ttlSessao : expiraCredencial;
    }
}
