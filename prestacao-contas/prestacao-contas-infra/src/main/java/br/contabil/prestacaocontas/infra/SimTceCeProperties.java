package br.contabil.prestacaocontas.infra;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;

import br.contabil.prestacaocontas.domain.CampoLayoutRemessaSimTceCe;
import br.contabil.prestacaocontas.domain.LayoutRemessaSimTceCe;

/** Propriedades do leiaute SIM/TCE-CE. O manual anual entra como configuração. */
@ConfigurationProperties(prefix = "siafic.prestacao-contas.sim-tce-ce")
public class SimTceCeProperties {

    private Layout tabela308 = new Layout();

    public Layout getTabela308() {
        return tabela308;
    }

    public void setTabela308(Layout tabela308) {
        this.tabela308 = tabela308;
    }

    public Layout tabela308() {
        return tabela308;
    }

    public static class Layout {
        private String tabela;
        private String prefixoArquivo;
        private String extensaoArquivo;
        private String delimitador;
        private int quantidadeCampos;
        private List<Campo> campos = new ArrayList<>();

        public String getTabela() {
            return tabela;
        }

        public void setTabela(String tabela) {
            this.tabela = tabela;
        }

        public String getPrefixoArquivo() {
            return prefixoArquivo;
        }

        public void setPrefixoArquivo(String prefixoArquivo) {
            this.prefixoArquivo = prefixoArquivo;
        }

        public String getExtensaoArquivo() {
            return extensaoArquivo;
        }

        public void setExtensaoArquivo(String extensaoArquivo) {
            this.extensaoArquivo = extensaoArquivo;
        }

        public String getDelimitador() {
            return delimitador;
        }

        public void setDelimitador(String delimitador) {
            this.delimitador = delimitador;
        }

        public int getQuantidadeCampos() {
            return quantidadeCampos;
        }

        public void setQuantidadeCampos(int quantidadeCampos) {
            this.quantidadeCampos = quantidadeCampos;
        }

        public List<Campo> getCampos() {
            return campos;
        }

        public void setCampos(List<Campo> campos) {
            this.campos = campos;
        }

        LayoutRemessaSimTceCe paraDominio() {
            return new LayoutRemessaSimTceCe(
                    tabela,
                    prefixoArquivo,
                    extensaoArquivo,
                    delimitador,
                    quantidadeCampos,
                    campos.stream().map(Campo::paraDominio).toList());
        }
    }

    public static class Campo {
        private String nome;
        private String origem;
        private String valorFixo;

        public String getNome() {
            return nome;
        }

        public void setNome(String nome) {
            this.nome = nome;
        }

        public String getOrigem() {
            return origem;
        }

        public void setOrigem(String origem) {
            this.origem = origem;
        }

        public String getValorFixo() {
            return valorFixo;
        }

        public void setValorFixo(String valorFixo) {
            this.valorFixo = valorFixo;
        }

        CampoLayoutRemessaSimTceCe paraDominio() {
            return new CampoLayoutRemessaSimTceCe(
                    nome,
                    Optional.ofNullable(origem),
                    Optional.ofNullable(valorFixo));
        }
    }
}
