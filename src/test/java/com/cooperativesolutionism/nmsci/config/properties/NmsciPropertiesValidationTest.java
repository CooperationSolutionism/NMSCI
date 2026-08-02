package com.cooperativesolutionism.nmsci.config.properties;

import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class NmsciPropertiesValidationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(validProperties());

    @Test
    void startsWithValidProperties() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(NmsciProperties.class).getCentralPubkeyBase64())
                    .isEqualTo(TestKeyPairs.CENTRAL.pubkeyBase64());
        });
    }

    @Test
    void failsFastWhenCentralPubkeyIsInvalid() {
        contextRunner
                .withPropertyValues("nmsci.central-key-pair.pubkey=not-base64")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsFastWhenCentralPubkeyDoesNotMatchPrikey() {
        contextRunner
                .withPropertyValues("nmsci.central-key-pair.pubkey=" + TestKeyPairs.FLOW_NODE_A.pubkeyBase64())
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsFastWhenCentralPubkeyPrefixIsInvalid() {
        String invalidPubkey = pubkeyBase64WithPrefix((byte) 0x04);
        NmsciProperties.CentralKeyPair keyPair = new NmsciProperties.CentralKeyPair();
        keyPair.setPubkey(invalidPubkey);
        keyPair.setPrikey(TestKeyPairs.CENTRAL.prikeyBase64());

        assertThat(keyPair.isPubkeyValid()).isFalse();
        assertThat(keyPair.isKeyPairMatched()).isTrue();
        contextRunner
                .withPropertyValues("nmsci.central-key-pair.pubkey=" + invalidPubkey)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsFastWhenSourceCodeHashIsInvalid() {
        contextRunner
                .withPropertyValues("nmsci.source-code-zip-hash=bad")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsFastWhenBlockSizeIsNotPositive() {
        contextRunner
                .withPropertyValues("nmsci.block-max-size=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsFastWhenBlockMaxSizeCannotFitHeader() {
        contextRunner
                .withPropertyValues("nmsci.block-header-size=229", "nmsci.block-max-size=128")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void acceptsLatestSupportedBlockVersion() {
        contextRunner
                .withPropertyValues("nmsci.block-version=3")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void failsFastWhenBlockVersionExceedsVerifierSupport() {
        contextRunner
                .withPropertyValues("nmsci.block-version=4")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsFastWhenDatMaxSizeCannotFitOneBlock() {
        contextRunner
                .withPropertyValues("nmsci.block-max-size=1048576", "nmsci.block-dat-max-size=1048576")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsFastWhenBlockIntervalIsNotPositive() {
        contextRunner
                .withPropertyValues("nmsci.block-interval-ms=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsFastWhenPathIsBlank() {
        contextRunner
                .withPropertyValues("nmsci.file-root-dir=   ")
                .run(context -> assertThat(context).hasFailed());
    }

    private static String[] validProperties() {
        return new String[]{
                "nmsci.central-key-pair.pubkey=" + TestKeyPairs.CENTRAL.pubkeyBase64(),
                "nmsci.central-key-pair.prikey=" + TestKeyPairs.CENTRAL.prikeyBase64(),
                "nmsci.block-version=1",
                "nmsci.block-header-size=229",
                "nmsci.block-max-size=1048576",
                "nmsci.block-dat-max-size=134217728",
                "nmsci.block-interval-ms=600000",
                "nmsci.register-difficulty-target-nbits=0x20ffffff",
                "nmsci.transaction-difficulty-target-nbits=0x20ffffff",
                "nmsci.file-root-dir=target/nmsci-test-files",
                "nmsci.file-dat-dir=dat",
                "nmsci.file-source-code-dir=source-code",
                "nmsci.source-code-zip-hash=0000000000000000000000000000000000000000000000000000000000000000"
        };
    }

    private static String pubkeyBase64WithPrefix(byte prefix) {
        byte[] pubkey = Base64.getDecoder().decode(TestKeyPairs.CENTRAL.pubkeyBase64());
        pubkey[0] = prefix;
        return Base64.getEncoder().encodeToString(pubkey);
    }

    @EnableConfigurationProperties(NmsciProperties.class)
    static class TestConfig {
    }
}
