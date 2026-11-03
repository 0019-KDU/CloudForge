package com.cloudforge.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpaqueTokenTest {

    @Test
    void generatesUrlSafeTokensWith256BitsOfEntropy() {
        OpaqueToken token = OpaqueToken.generate();

        assertThat(token.value()).matches("[A-Za-z0-9_-]{43}");
        assertThat(OpaqueToken.generate().value()).isNotEqualTo(token.value());
    }

    @Test
    void hashIsDeterministicHexSha256() {
        OpaqueToken token = new OpaqueToken("abc");

        assertThat(token.hash())
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
                .isEqualTo(new OpaqueToken("abc").hash());
    }

    @Test
    void toStringNeverRevealsTheSecret() {
        OpaqueToken token = OpaqueToken.generate();

        assertThat(token.toString()).doesNotContain(token.value());
    }
}
