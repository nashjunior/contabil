package br.contabil.execucao.infra.documento;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "siafic.execucao.empenho-documento.outbox")
public class EmpenhoDocumentoPendenteProperties {

    private boolean enabled;
    private String bucket = "ged";
    private int batchSize = 50;
    private int maxTentativas = 5;
    private long intervaloMs = 10_000;
    private Duration lockDuration = Duration.ofMinutes(2);
    private Duration backoffBase = Duration.ofSeconds(5);
    private Duration backoffMax = Duration.ofMinutes(15);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxTentativas() {
        return maxTentativas;
    }

    public void setMaxTentativas(int maxTentativas) {
        this.maxTentativas = maxTentativas;
    }

    public long getIntervaloMs() {
        return intervaloMs;
    }

    public void setIntervaloMs(long intervaloMs) {
        this.intervaloMs = intervaloMs;
    }

    public Duration getLockDuration() {
        return lockDuration;
    }

    public void setLockDuration(Duration lockDuration) {
        this.lockDuration = lockDuration;
    }

    public Duration getBackoffBase() {
        return backoffBase;
    }

    public void setBackoffBase(Duration backoffBase) {
        this.backoffBase = backoffBase;
    }

    public Duration getBackoffMax() {
        return backoffMax;
    }

    public void setBackoffMax(Duration backoffMax) {
        this.backoffMax = backoffMax;
    }

    int batchSizeEfetivo() {
        return Math.max(1, batchSize);
    }

    int maxTentativasEfetivo() {
        return Math.max(1, maxTentativas);
    }

    Duration lockDurationEfetivo() {
        return lockDuration.isNegative() || lockDuration.isZero() ? Duration.ofMinutes(2) : lockDuration;
    }

    Duration backoffBaseEfetivo() {
        return backoffBase.isNegative() || backoffBase.isZero() ? Duration.ofSeconds(5) : backoffBase;
    }

    Duration backoffMaxEfetivo() {
        return backoffMax.isNegative() || backoffMax.isZero() ? Duration.ofMinutes(15) : backoffMax;
    }
}
